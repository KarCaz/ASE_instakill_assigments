<template>
  <div class="app-shell">
    <header class="app-header">
      <h1>Instakill</h1>
      <nav v-if="user">
        <span class="user">@{{ user.username }}</span>
        <button @click="showAnalytics = !showAnalytics; showAnalytics && loadAnalytics()">
          {{ showAnalytics ? 'Back to feed' : 'My analytics' }}
        </button>
        <button @click="logout">Logout</button>
      </nav>
    </header>

    <main class="content">
      <section v-if="!user" class="card">
        <div class="tabs">
          <button :class="{ active: authMode === 'login' }" @click="authMode = 'login'">Login</button>
          <button :class="{ active: authMode === 'register' }" @click="authMode = 'register'">Register</button>
        </div>
        <form @submit.prevent="submitAuth" class="form">
          <input v-model="authForm.usernameOrEmail" v-if="authMode === 'login'" placeholder="Username or email" required>
          <input v-model="authForm.username" v-else placeholder="Username" required minlength="3">
          <input v-model="authForm.email" v-if="authMode === 'register'" type="email" placeholder="Email" required>
          <input v-model="authForm.password" type="password" placeholder="Password" required minlength="6">
          <p v-if="error" class="error">{{ error }}</p>
          <button type="submit">Continue</button>
        </form>
      </section>

      <section v-else-if="showAnalytics" class="analytics">
        <div class="feed-toolbar">
          <h2>My analytics</h2>
          <label class="percentile-control">
            Top
            <select v-model.number="topPercent" @change="loadAnalytics">
              <option :value="5">5%</option>
              <option :value="10">10%</option>
              <option :value="25">25%</option>
              <option :value="50">50%</option>
            </select>
            ranking
          </label>
        </div>

        <p v-if="analyticsLoading" class="hint">Calculating your statistics...</p>
        <p v-if="analyticsError" class="error">{{ analyticsError }}</p>
        <template v-if="analytics && !analyticsLoading">
          <div class="analytics-summary">
            <article class="card stat-card">
              <span>Total likes</span><strong>{{ analytics.interactionRatio.likes }}</strong>
            </article>
            <article class="card stat-card">
              <span>Total comments</span><strong>{{ analytics.interactionRatio.comments }}</strong>
            </article>
            <article class="card stat-card">
              <span>Like-to-comment ratio</span>
              <strong>{{ analytics.interactionRatio.likeToCommentRatio == null ? 'N/A' : analytics.interactionRatio.likeToCommentRatio.toFixed(2) }}</strong>
            </article>
            <article class="card stat-card">
              <span>Average engagement</span><strong>{{ analytics.engagementRanking.userAverageEngagement.toFixed(2) }}</strong>
            </article>
          </div>

          <section class="card">
            <h3>Weekly activity</h3>
            <p v-if="!analytics.weeklyActivity.length" class="hint">No activity yet.</p>
            <div v-else class="table-scroll">
              <table>
                <thead><tr><th>Week (YYYY-WW)</th><th>Posts</th><th>Likes</th><th>Comments</th></tr></thead>
                <tbody>
                  <tr v-for="week in analytics.weeklyActivity" :key="week.week">
                    <td>{{ week.week }}</td><td>{{ week.posts }}</td><td>{{ week.likes }}</td><td>{{ week.comments }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section class="card">
            <h3>Top 5 posts by engagement</h3>
            <p v-if="!analytics.topPosts.length" class="hint">You have not created any posts yet.</p>
            <ol v-else class="top-post-list">
              <li v-for="post in analytics.topPosts" :key="post.postId">
                <div><strong>{{ post.content }}</strong><small>{{ formatDate(post.createdAt) }}</small></div>
                <span>{{ post.engagement }} engagement ({{ post.likes }} likes · {{ post.comments }} comments)</span>
              </li>
            </ol>
          </section>

          <section class="card">
            <h3>Average likes by media type</h3>
            <p v-if="!analytics.averageLikesByMediaType.length" class="hint">No photo or video posts to compare yet.</p>
            <div v-else class="media-average-list">
              <div v-for="item in analytics.averageLikesByMediaType" :key="item.mediaType">
                <strong>{{ item.mediaType }}</strong>
                <span>{{ item.averageLikes.toFixed(2) }} average likes across {{ item.posts }} posts</span>
              </div>
            </div>
          </section>

          <section class="card ranking-card">
            <h3>Engagement ranking</h3>
            <p>Your average engagement is <strong>{{ analytics.engagementRanking.userAverageEngagement.toFixed(2) }}</strong> per post.</p>
            <p>Rank: <strong>{{ analytics.engagementRanking.rank }} of {{ analytics.engagementRanking.totalUsers }}</strong> users.</p>
            <p :class="analytics.engagementRanking.inTopPercent ? 'ranking-good' : 'hint'">
              {{ analytics.engagementRanking.inTopPercent
                ? `You are in the top ${analytics.engagementRanking.topPercent}% of users.`
                : `You are not currently in the top ${analytics.engagementRanking.topPercent}% of users.` }}
            </p>
            <small>Users are ranked by average likes + comments per post. Users without posts are included with an average of zero.</small>
          </section>
        </template>
      </section>

      <section v-else class="feed">
        <form @submit.prevent="createNewPost" class="card form composer">
          <textarea v-model="composer.content" rows="3" placeholder="Share something..." required></textarea>
          <input v-model="composer.imageUrl" placeholder="Image/video URL (optional) or upload a file below" :disabled="uploading">
          <input type="file" accept="image/*,video/*" :disabled="uploading" @change="onFileChosen">
          <p v-if="uploading" class="hint">Uploading...</p>
          <p v-if="composerError" class="error">{{ composerError }}</p>
          <div class="actions">
            <button type="submit" :disabled="uploading">Post</button>
          </div>
        </form>

        <div class="feed-toolbar">
          <h2>Feed</h2>
          <div class="sort">
            <button :class="{ active: sort === 'new' }" @click="changeSort('new')">Newest</button>
            <button :class="{ active: sort === 'trending' }" @click="changeSort('trending')">Trending</button>
          </div>
        </div>

        <article v-for="post in posts" :key="post.id" class="card post">
          <header>
            <div>
              <h3>@{{ post.author.username }}</h3>
              <small>{{ formatDate(post.createdAt) }}</small>
            </div>
            <div class="metrics">
              <span>{{ post.likeCount }} likes</span>
              <span> | </span>
              <span>{{ post.commentCount }} comments</span>
            </div>
          </header>
          <p>{{ post.content }}</p>
          <video v-if="post.imageUrl && isVideo(post.imageUrl)" :src="post.imageUrl" controls></video>
          <img v-else-if="post.imageUrl" :src="post.imageUrl" alt="post image">

          <!-- Comments Section -->
          <section class="comments">
            <div v-if="comments[post.id] && comments[post.id].length">
              <div v-for="comment in comments[post.id]" :key="comment.id" class="comment">
                <b>@{{ comment.author.username }}</b> <small>{{ formatDate(comment.createdAt) }}</small>
                <div>{{ comment.content }}</div>
              </div>
            </div>
            <div v-else class="no-comments">
              <em>No comments yet.</em>
            </div>
            <form v-if="user" @submit.prevent="submitComment(post.id)" class="comment-form">
              <input
                v-model="newComment[post.id]"
                :placeholder="'Add a comment...'"
                required
                minlength="1"
              />
              <button type="submit" :disabled="commentLoading[post.id]">Comment</button>
            </form>
            <div v-if="commentError[post.id]" class="error">{{ commentError[post.id] }}</div>
          </section>

          <footer>
            <button @click="togglePostLike(post)">
              {{ post.likedByCurrentUser ? 'Unlike' : 'Like' }}
            </button>
          </footer>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createPost, fetchMe, listFeed, login, register as registerUser, toggleLike, listComments, addComment, uploadMedia, fetchAnalytics } from './api'

const user = ref(null)
const posts = ref([])
const sort = ref('new')
const authMode = ref('login')
const error = ref('')
const authForm = reactive({ username: '', email: '', password: '', usernameOrEmail: '' })
const composer = reactive({ content: '', imageUrl: '' })
const uploading = ref(false)
const composerError = ref('')

// Comments state
const comments = reactive({})
const newComment = reactive({})
const commentLoading = reactive({})
const commentError = reactive({})
const showAnalytics = ref(false)
const analytics = ref(null)
const analyticsLoading = ref(false)
const analyticsError = ref('')
const topPercent = ref(10)

onMounted(async () => {
  await bootstrap()
})

async function bootstrap() {
  try {
    user.value = await fetchMe()
    await loadFeed()
  } catch (e) {
    user.value = null
  }
}

async function submitAuth() {
  error.value = ''
  try {
    if (authMode.value === 'login') {
      const result = await login({
        usernameOrEmail: authForm.usernameOrEmail,
        password: authForm.password
      })
      user.value = result.user
    } else {
      const result = await registerUser({
        username: authForm.username,
        email: authForm.email,
        password: authForm.password
      })
      user.value = result.user
    }
    await loadFeed()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Authentication failed'
  }
}

async function loadAnalytics() {
  analyticsLoading.value = true
  analyticsError.value = ''
  try {
    analytics.value = await fetchAnalytics({ topPercent: topPercent.value })
  } catch (e) {
    analyticsError.value = e.response?.data?.detail || 'Could not load analytics'
  } finally {
    analyticsLoading.value = false
  }
}

async function loadFeed() {
  posts.value = await listFeed({ sort: sort.value })
  // Load comments for each post
  for (const post of posts.value) {
    await loadComments(post.id)
  }
}

async function loadComments(postId) {
  try {
    commentError[postId] = ''
    comments[postId] = await listComments(postId)
  } catch (e) {
    comments[postId] = []
    commentError[postId] = 'Failed to load comments'
  }
}

async function submitComment(postId) {
  if (!newComment[postId] || !newComment[postId].trim()) return
  commentLoading[postId] = true
  commentError[postId] = ''
  try {
    await addComment(postId, { content: newComment[postId] })
    newComment[postId] = ''
    await loadComments(postId)
    // Optionally, update commentCount in post
    const post = posts.value.find(p => p.id === postId)
    if (post) post.commentCount = (comments[postId]?.length || 0)
  } catch (e) {
    commentError[postId] = e.response?.data?.detail || 'Failed to add comment'
  } finally {
    commentLoading[postId] = false
  }
}

async function onFileChosen(event) {
  const input = event.target
  const file = input.files && input.files[0]
  if (!file) return
  composerError.value = ''
  uploading.value = true
  try {
    // Upload first, then reuse the returned URL as the post's imageUrl.
    const { mediaUrl } = await uploadMedia(file)
    composer.imageUrl = mediaUrl
  } catch (e) {
    composerError.value = e.response?.data?.detail || 'Upload failed'
    input.value = ''
  } finally {
    uploading.value = false
  }
}

async function createNewPost() {
  if (!composer.content.trim()) return
  composerError.value = ''
  try {
    await createPost({ content: composer.content, imageUrl: composer.imageUrl || null })
  } catch (e) {
    composerError.value = e.response?.data?.detail || 'Failed to create post'
    return
  }
  composer.content = ''
  composer.imageUrl = ''
  await loadFeed()
}

function isVideo(url) {
  return /\.(mp4|mov|webm)(\?.*)?$/i.test(url)
}

async function togglePostLike(post) {
  const result = await toggleLike(post.id)
  post.likedByCurrentUser = result.liked
  post.likeCount = result.likeCount
}

function changeSort(next) {
  if (sort.value === next) return
  sort.value = next
  loadFeed()
}

function formatDate(iso) {
  return new Date(iso).toLocaleString()
}

async function logout() {
  try {
    await fetch('/auth/logout', { method: 'POST', credentials: 'include' })
  } catch (e) {
    // ignore
  }
  document.cookie = 'ACCESS_TOKEN=; Max-Age=0; path=/'
  user.value = null
  posts.value = []
  analytics.value = null
  showAnalytics.value = false
}
</script>

<style scoped>
.app-shell {
  max-width: 960px;
  margin: 0 auto;
  padding: 2rem 1rem;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.app-header nav {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.app-header button {
  background: #111827;
  color: #fff;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 999px;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.card {
  background: #fff;
  border-radius: 16px;
  padding: 1.5rem;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.1);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.form input,
.form textarea {
  width: 100%;
  padding: 0.75rem;
  border-radius: 10px;
  border: 1px solid #d1d5db;
}

.form button {
  align-self: flex-start;
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.5rem 1.25rem;
  border-radius: 999px;
}

.tabs {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
}

.tabs button {
  background: transparent;
  border: none;
  font-weight: 600;
  color: #6b7280;
}

.tabs button.active {
  color: #111827;
}

.error {
  color: #ef4444;
}

.feed-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sort button {
  margin-left: 0.5rem;
  border: none;
  background: #e5e7eb;
  padding: 0.4rem 0.9rem;
  border-radius: 999px;
}

.sort button.active {
  background: #111827;
  color: #fff;
}

.post header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.hint {
  color: #6b7280;
  margin: 0;
}

.post img,
.post video {
  margin-top: 0.75rem;
  width: 100%;
  border-radius: 12px;
}

.post footer {
  margin-top: 1rem;
}

.post footer button {
  background: #111827;
  border: none;
  color: #fff;
  padding: 0.4rem 1.2rem;
  border-radius: 999px;
}
.comments {
  margin-top: 1.2rem;
  padding-top: 1rem;
  border-top: 1px solid #e5e7eb;
}
.comment {
  margin-bottom: 0.7rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #f3f4f6;
}
.no-comments {
  color: #6b7280;
  margin-bottom: 0.7rem;
}
.comment-form {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
.comment-form input {
  flex: 1;
  padding: 0.5rem;
  border-radius: 8px;
  border: 1px solid #d1d5db;
}
.comment-form button {
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.4rem 1.1rem;
  border-radius: 999px;
}

.analytics { display: flex; flex-direction: column; gap: 1rem; }
.analytics-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 1rem; }
.stat-card { display: flex; flex-direction: column; gap: .5rem; }
.stat-card span, .hint { color: #6b7280; }
.stat-card strong { font-size: 1.7rem; }
.percentile-control { display: flex; align-items: center; gap: .4rem; }
.percentile-control select { padding: .4rem; border: 1px solid #d1d5db; border-radius: 8px; }
.table-scroll { overflow-x: auto; }
.analytics table { width: 100%; border-collapse: collapse; text-align: left; }
.analytics th, .analytics td { padding: .65rem; border-bottom: 1px solid #e5e7eb; }
.top-post-list { padding-left: 1.4rem; }
.top-post-list li { padding: .6rem 0; }
.top-post-list li div { display: flex; flex-direction: column; gap: .25rem; }
.top-post-list small, .ranking-card small { color: #6b7280; }
.media-average-list > div { display: flex; justify-content: space-between; gap: 1rem; padding: .6rem 0; border-bottom: 1px solid #e5e7eb; }
.ranking-good { color: #15803d; font-weight: 600; }
@media (max-width: 600px) {
  .media-average-list > div { flex-direction: column; gap: .25rem; }
  .app-header { align-items: flex-start; gap: 1rem; }
  .app-header nav { flex-wrap: wrap; justify-content: flex-end; }
}
</style>
