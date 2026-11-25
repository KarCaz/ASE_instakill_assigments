"""
Locust load testing script for Instakill application.

This script simulates realistic user behavior to test the limits of a single server
with a single database. It helps demonstrate performance bottlenecks for educational purposes.

Usage:
    locust --host=http://localhost:8080

Or with web UI:
    locust --host=http://localhost:8080 --web-host=127.0.0.1 --web-port=8089

Or headless mode:
    locust --host=http://localhost:8080 --headless --users 10 --spawn-rate 2 --run-time 60s
"""

from locust import HttpUser, task, between, events
import random
import json
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class InstakillUser(HttpUser):
    """
    Simulates a user of the Instakill social media platform.
    
    This user performs common actions:
    - Login
    - Browse the news feed (different sort orders)
    - View analytics/stats
    - Create new posts
    - Like posts
    - Add comments to posts
    """
    
    # Wait between 1 to 5 seconds between tasks to simulate real user behavior
    wait_time = between(1, 5)
    
    # Available test users (from seed data)
    test_users = [
        {"usernameOrEmail": "alice", "password": "password"},
        {"usernameOrEmail": "bob", "password": "password"}
    ]
    
    def on_start(self):
        """Called when a simulated user starts. Login to get authenticated."""
        # Pick a random test user
        self.user_credentials = random.choice(self.test_users)
        self.login()
    
    def login(self):
        """Authenticate the user and store the session cookie."""
        response = self.client.post(
            "/api/auth/login",
            json=self.user_credentials,
            name="/api/auth/login"
        )
        
        if response.status_code == 200:
            logger.info(f"User {self.user_credentials['usernameOrEmail']} logged in successfully")
            # The ACCESS_TOKEN cookie is automatically handled by the session
        else:
            logger.error(f"Login failed for {self.user_credentials['usernameOrEmail']}: {response.status_code}")
    
    @task(5)
    def view_feed_new(self):
        """View the news feed sorted by newest posts (most common action)."""
        self.client.get(
            "/api/feed?sort=new&page=0&size=10",
            name="/api/feed [sort=new]"
        )
    
    @task(3)
    def view_feed_hot(self):
        """View the news feed sorted by hot/trending posts."""
        self.client.get(
            "/api/feed?sort=hot&page=0&size=10",
            name="/api/feed [sort=hot]"
        )
    
    @task(2)
    def view_feed_top(self):
        """View the news feed sorted by top posts."""
        self.client.get(
            "/api/feed?sort=top&page=0&size=10",
            name="/api/feed [sort=top]"
        )
    
    @task(4)
    def view_analytics(self):
        """View user analytics and stats."""
        self.client.get(
            "/api/analytics/me",
            name="/api/analytics/me"
        )
    
    @task(2)
    def create_post(self):
        """Create a new post with random content."""
        post_content = self._generate_post_content()
        
        response = self.client.post(
            "/api/posts",
            json={
                "content": post_content,
                "imageUrl": None
            },
            name="/api/posts [create]"
        )
        
        if response.status_code == 201:
            logger.debug(f"Post created: {post_content[:30]}...")
        else:
            logger.warning(f"Failed to create post: {response.status_code}")
    
    @task(3)
    def like_random_post(self):
        """Toggle like on a random post from the feed."""
        # First, get the feed to find posts
        response = self.client.get(
            "/api/feed?sort=new&page=0&size=10",
            name="/api/feed [for liking]"
        )
        
        if response.status_code == 200:
            try:
                posts = response.json()
                if posts and len(posts) > 0:
                    # Pick a random post
                    random_post = random.choice(posts)
                    post_id = random_post.get("id")
                    
                    if post_id:
                        # Toggle like on the post
                        self.client.post(
                            f"/api/posts/{post_id}/likes/toggle",
                            name="/api/posts/:id/likes/toggle"
                        )
            except (json.JSONDecodeError, KeyError) as e:
                logger.warning(f"Error parsing feed for liking: {e}")
    
    @task(2)
    def add_comment(self):
        """Add a comment to a random post from the feed."""
        # First, get the feed to find posts
        response = self.client.get(
            "/api/feed?sort=new&page=0&size=10",
            name="/api/feed [for commenting]"
        )
        
        if response.status_code == 200:
            try:
                posts = response.json()
                if posts and len(posts) > 0:
                    # Pick a random post
                    random_post = random.choice(posts)
                    post_id = random_post.get("id")
                    
                    if post_id:
                        # Add a comment
                        comment_content = self._generate_comment_content()
                        self.client.post(
                            f"/api/posts/{post_id}/comments",
                            json={"content": comment_content},
                            name="/api/posts/:id/comments"
                        )
            except (json.JSONDecodeError, KeyError) as e:
                logger.warning(f"Error parsing feed for commenting: {e}")
    
    @task(1)
    def view_post_details(self):
        """View details of a specific post."""
        # First, get the feed to find posts
        response = self.client.get(
            "/api/feed?sort=new&page=0&size=10",
            name="/api/feed [for viewing]"
        )
        
        if response.status_code == 200:
            try:
                posts = response.json()
                if posts and len(posts) > 0:
                    # Pick a random post
                    random_post = random.choice(posts)
                    post_id = random_post.get("id")
                    
                    if post_id:
                        # Get post details
                        self.client.get(
                            f"/api/posts/{post_id}",
                            name="/api/posts/:id [get]"
                        )
            except (json.JSONDecodeError, KeyError) as e:
                logger.warning(f"Error parsing feed for viewing: {e}")
    
    @task(1)
    def paginate_feed(self):
        """Browse through multiple pages of the feed."""
        page = random.randint(0, 3)
        self.client.get(
            f"/api/feed?sort=new&page={page}&size=10",
            name="/api/feed [paginated]"
        )
    
    def _generate_post_content(self):
        """Generate random post content."""
        templates = [
            "Just had an amazing day! 🌟",
            "Check out this cool thing I found!",
            "Loving the weather today ☀️",
            "Can't believe how fast this week went by",
            "New post alert! What do you think?",
            "Just sharing some thoughts...",
            "Happy to be here with all of you!",
            "This is getting interesting 🤔",
            "Weekend vibes! 🎉",
            "Trying out the new features!"
        ]
        return random.choice(templates)
    
    def _generate_comment_content(self):
        """Generate random comment content."""
        templates = [
            "Great post!",
            "Love this! 😍",
            "Totally agree!",
            "This is amazing!",
            "Thanks for sharing!",
            "Interesting perspective!",
            "Well said!",
            "Couldn't agree more!",
            "This made my day!",
            "So true! 👍"
        ]
        return random.choice(templates)


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """Called when the test starts."""
    logger.info("=" * 60)
    logger.info("Starting Instakill Load Test")
    logger.info("=" * 60)
    logger.info(f"Host: {environment.host}")
    logger.info("Test users: alice, bob, charlie (password: password)")
    logger.info("=" * 60)


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """Called when the test stops."""
    logger.info("=" * 60)
    logger.info("Instakill Load Test Completed")
    logger.info("=" * 60)
