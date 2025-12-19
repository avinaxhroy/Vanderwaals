#!/usr/bin/env python3
"""
GitHub API Client for Incremental Update Detection
===================================================

Provides functionality to detect repository changes using GitHub's REST API,
enabling smart incremental updates that only process new/modified wallpapers.

Features:
- Get latest commit SHA for any branch
- Compare commits to find changed files
- Check if repository has updates since last run
- Rate limiting aware with retry logic
"""

import os
import time
import logging
from typing import Dict, List, Optional, Set, Tuple
from dataclasses import dataclass
from datetime import datetime
import requests

logger = logging.getLogger(__name__)

# Rate limiting configuration
RATE_LIMIT_BUFFER = 100  # Reserve this many requests
RETRY_DELAY_SECONDS = 5
MAX_RETRIES = 3


@dataclass
class RepoInfo:
    """Repository information with update status."""
    owner: str
    repo: str
    branch: str
    latest_sha: str
    has_updates: bool
    changed_files: List[str]
    last_checked: str


class GitHubAPIClient:
    """
    GitHub API client for repository change detection.
    
    Uses GitHub REST API to efficiently check for repository updates
    and identify which files have changed since last processing.
    
    Supports both authenticated (higher rate limits) and unauthenticated modes.
    """
    
    def __init__(self, token: Optional[str] = None):
        """
        Initialize GitHub API client.
        
        Args:
            token: GitHub personal access token or GITHUB_TOKEN from Actions.
                   If None, will try to read from GITHUB_TOKEN env var.
        """
        self.base_url = "https://api.github.com"
        self.token = token or os.environ.get("GITHUB_TOKEN")
        self.session = requests.Session()
        
        # Set up headers
        self.session.headers.update({
            "Accept": "application/vnd.github.v3+json",
            "User-Agent": "Vanderwaals-Curation-Pipeline"
        })
        
        if self.token:
            self.session.headers["Authorization"] = f"Bearer {self.token}"
            logger.info("GitHub API client initialized with authentication")
        else:
            logger.warning("GitHub API client running without authentication (lower rate limits)")
        
        # Track rate limits
        self.rate_limit_remaining = None
        self.rate_limit_reset = None
    
    def _update_rate_limits(self, response: requests.Response):
        """Update rate limit tracking from response headers."""
        try:
            self.rate_limit_remaining = int(response.headers.get("X-RateLimit-Remaining", 0))
            self.rate_limit_reset = int(response.headers.get("X-RateLimit-Reset", 0))
        except (ValueError, TypeError):
            pass
    
    def _check_rate_limit(self):
        """Check if we're approaching rate limit and wait if necessary."""
        if self.rate_limit_remaining is not None and self.rate_limit_remaining < RATE_LIMIT_BUFFER:
            if self.rate_limit_reset:
                wait_time = max(0, self.rate_limit_reset - time.time()) + 1
                if wait_time > 0 and wait_time < 3600:  # Don't wait more than 1 hour
                    logger.warning(f"Rate limit low ({self.rate_limit_remaining}), waiting {wait_time:.0f}s")
                    time.sleep(wait_time)
    
    def _make_request(self, endpoint: str, params: Optional[Dict] = None) -> Optional[Dict]:
        """
        Make authenticated request to GitHub API with retry logic.
        
        Args:
            endpoint: API endpoint (e.g., "/repos/owner/repo/commits/main")
            params: Optional query parameters
            
        Returns:
            JSON response as dict, or None if request failed
        """
        self._check_rate_limit()
        
        url = f"{self.base_url}{endpoint}"
        
        for attempt in range(MAX_RETRIES):
            try:
                response = self.session.get(url, params=params, timeout=30)
                self._update_rate_limits(response)
                
                if response.status_code == 200:
                    return response.json()
                elif response.status_code == 403 and "rate limit" in response.text.lower():
                    logger.warning(f"Rate limit exceeded, waiting...")
                    time.sleep(60)
                    continue
                elif response.status_code == 404:
                    logger.warning(f"Not found: {endpoint}")
                    return None
                else:
                    logger.error(f"API error {response.status_code}: {response.text[:200]}")
                    if attempt < MAX_RETRIES - 1:
                        time.sleep(RETRY_DELAY_SECONDS)
                    continue
                    
            except requests.exceptions.Timeout:
                logger.warning(f"Request timeout for {endpoint}, attempt {attempt + 1}/{MAX_RETRIES}")
                if attempt < MAX_RETRIES - 1:
                    time.sleep(RETRY_DELAY_SECONDS)
                continue
            except requests.exceptions.RequestException as e:
                logger.error(f"Request failed: {e}")
                if attempt < MAX_RETRIES - 1:
                    time.sleep(RETRY_DELAY_SECONDS)
                continue
        
        return None
    
    def get_latest_commit_sha(self, owner: str, repo: str, branch: str) -> Optional[str]:
        """
        Get the latest commit SHA for a branch.
        
        Args:
            owner: Repository owner (e.g., "dharmx")
            repo: Repository name (e.g., "walls")
            branch: Branch name (e.g., "main")
            
        Returns:
            Commit SHA string, or None if not found
        """
        endpoint = f"/repos/{owner}/{repo}/commits/{branch}"
        data = self._make_request(endpoint)
        
        if data and "sha" in data:
            return data["sha"]
        return None
    
    def get_changed_files(
        self, 
        owner: str, 
        repo: str, 
        old_sha: str, 
        new_sha: str,
        extensions: Optional[Set[str]] = None
    ) -> List[str]:
        """
        Get list of files changed between two commits.
        
        Args:
            owner: Repository owner
            repo: Repository name
            old_sha: Previous commit SHA
            new_sha: Current commit SHA
            extensions: Optional set of file extensions to filter (e.g., {'.jpg', '.png'})
            
        Returns:
            List of changed file paths
        """
        endpoint = f"/repos/{owner}/{repo}/compare/{old_sha}...{new_sha}"
        data = self._make_request(endpoint)
        
        if not data or "files" not in data:
            return []
        
        changed_files = []
        for file_info in data["files"]:
            filename = file_info.get("filename", "")
            status = file_info.get("status", "")
            
            # Skip deleted files
            if status == "removed":
                continue
            
            # Filter by extension if specified
            if extensions:
                ext = os.path.splitext(filename)[1].lower()
                if ext not in extensions:
                    continue
            
            changed_files.append(filename)
        
        logger.info(f"Found {len(changed_files)} changed files between {old_sha[:7]}...{new_sha[:7]}")
        return changed_files
    
    def get_repo_tree(
        self, 
        owner: str, 
        repo: str, 
        branch: str,
        extensions: Optional[Set[str]] = None
    ) -> List[str]:
        """
        Get full file tree of repository.
        
        Useful for initial full sync or when comparison base is unavailable.
        
        Args:
            owner: Repository owner
            repo: Repository name
            branch: Branch name
            extensions: Optional set of file extensions to filter
            
        Returns:
            List of file paths in repository
        """
        # First get the commit to find tree SHA
        commit_data = self._make_request(f"/repos/{owner}/{repo}/commits/{branch}")
        if not commit_data:
            return []
        
        tree_sha = commit_data.get("commit", {}).get("tree", {}).get("sha")
        if not tree_sha:
            return []
        
        # Get recursive tree
        tree_data = self._make_request(
            f"/repos/{owner}/{repo}/git/trees/{tree_sha}",
            params={"recursive": "1"}
        )
        
        if not tree_data or "tree" not in tree_data:
            return []
        
        files = []
        for item in tree_data["tree"]:
            if item.get("type") != "blob":
                continue
            
            path = item.get("path", "")
            
            # Filter by extension if specified
            if extensions:
                ext = os.path.splitext(path)[1].lower()
                if ext not in extensions:
                    continue
            
            files.append(path)
        
        return files
    
    def check_repo_has_updates(
        self, 
        owner: str, 
        repo: str, 
        branch: str, 
        last_sha: Optional[str]
    ) -> Tuple[bool, str, List[str]]:
        """
        Check if repository has new commits since last SHA.
        
        Args:
            owner: Repository owner
            repo: Repository name
            branch: Branch name
            last_sha: Last processed commit SHA (None for first run)
            
        Returns:
            Tuple of (has_updates, current_sha, changed_files)
            - has_updates: True if there are new commits
            - current_sha: Current commit SHA
            - changed_files: List of changed files (empty if no updates or first run)
        """
        current_sha = self.get_latest_commit_sha(owner, repo, branch)
        
        if not current_sha:
            logger.error(f"Could not get current SHA for {owner}/{repo}")
            return False, "", []
        
        # First run - no previous SHA
        if not last_sha:
            logger.info(f"{owner}/{repo}: First run, will process all files")
            return True, current_sha, []
        
        # Same SHA - no updates
        if current_sha == last_sha:
            logger.info(f"{owner}/{repo}: No updates (SHA: {current_sha[:7]})")
            return False, current_sha, []
        
        # Get changed files
        image_extensions = {'.jpg', '.jpeg', '.png', '.webp'}
        changed_files = self.get_changed_files(
            owner, repo, last_sha, current_sha, 
            extensions=image_extensions
        )
        
        if not changed_files:
            logger.info(f"{owner}/{repo}: Commits changed but no image files modified")
            return False, current_sha, []
        
        logger.info(f"{owner}/{repo}: {len(changed_files)} image files changed")
        return True, current_sha, changed_files
    
    def get_rate_limit_status(self) -> Dict:
        """Get current rate limit status."""
        data = self._make_request("/rate_limit")
        if data and "resources" in data:
            core = data["resources"].get("core", {})
            return {
                "limit": core.get("limit", 0),
                "remaining": core.get("remaining", 0),
                "reset": datetime.fromtimestamp(core.get("reset", 0)).isoformat()
            }
        return {}


class UpdateTracker:
    """
    Tracks last processed commit SHA for each repository.
    
    Persists state to JSON file for resume capability across runs.
    """
    
    def __init__(self, tracker_path: str = "update_tracker.json"):
        """
        Initialize update tracker.
        
        Args:
            tracker_path: Path to JSON file for state persistence
        """
        import json
        self.tracker_path = tracker_path
        self.data = self._load()
    
    def _load(self) -> Dict:
        """Load tracker data from file."""
        import json
        try:
            with open(self.tracker_path, 'r') as f:
                return json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            return {
                "last_updated": None,
                "repositories": {}
            }
    
    def save(self):
        """Save tracker data to file."""
        import json
        self.data["last_updated"] = datetime.utcnow().isoformat() + "Z"
        with open(self.tracker_path, 'w') as f:
            json.dump(self.data, f, indent=2)
        logger.info(f"Saved update tracker to {self.tracker_path}")
    
    def get_last_sha(self, repo_name: str) -> Optional[str]:
        """Get last processed commit SHA for a repository."""
        repo_data = self.data.get("repositories", {}).get(repo_name, {})
        return repo_data.get("last_commit_sha")
    
    def update_repo(self, repo_name: str, commit_sha: str, wallpaper_count: int):
        """Update repository tracking info after processing."""
        if "repositories" not in self.data:
            self.data["repositories"] = {}
        
        self.data["repositories"][repo_name] = {
            "last_commit_sha": commit_sha,
            "last_processed_at": datetime.utcnow().isoformat() + "Z",
            "wallpaper_count": wallpaper_count
        }
    
    def get_all_repos(self) -> Dict:
        """Get all tracked repository data."""
        return self.data.get("repositories", {})


# Convenience function for testing
def check_all_repos_for_updates(repos_config: List[Dict], tracker_path: str = "update_tracker.json") -> Dict[str, RepoInfo]:
    """
    Check all configured repositories for updates.
    
    Args:
        repos_config: List of repository configuration dicts
        tracker_path: Path to update tracker JSON file
        
    Returns:
        Dict mapping repo names to RepoInfo objects
    """
    client = GitHubAPIClient()
    tracker = UpdateTracker(tracker_path)
    
    results = {}
    
    for repo in repos_config:
        name = repo["name"]
        owner, repo_name = name.split("/")
        branch = repo.get("branch", "main")
        
        last_sha = tracker.get_last_sha(name)
        has_updates, current_sha, changed_files = client.check_repo_has_updates(
            owner, repo_name, branch, last_sha
        )
        
        results[name] = RepoInfo(
            owner=owner,
            repo=repo_name,
            branch=branch,
            latest_sha=current_sha,
            has_updates=has_updates,
            changed_files=changed_files,
            last_checked=datetime.utcnow().isoformat() + "Z"
        )
    
    return results


if __name__ == "__main__":
    # Test the client
    logging.basicConfig(level=logging.INFO)
    
    client = GitHubAPIClient()
    
    # Test getting latest commit
    sha = client.get_latest_commit_sha("dharmx", "walls", "main")
    print(f"Latest SHA for dharmx/walls: {sha}")
    
    # Test rate limit status
    status = client.get_rate_limit_status()
    print(f"Rate limit status: {status}")
