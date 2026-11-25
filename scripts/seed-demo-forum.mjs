// Seeds a realistic multi-user forum discussion with images into the local dev stack.
// Usage: node scripts/seed-demo-forum.mjs
const API = 'http://localhost:8080/api/v1'
const MAILPIT = 'http://localhost:8025/api/v1'
const PASSWORD = 'secret123'

async function request(path, { method = 'GET', token, body } = {}) {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  const text = await response.text()
  let json = null
  try {
    json = JSON.parse(text)
  } catch {
    // non-JSON error body
  }
  return { status: response.status, json, text }
}

async function findVerificationToken(email) {
  for (let attempt = 0; attempt < 40; attempt++) {
    try {
      const list = await (await fetch(`${MAILPIT}/messages`)).json()
      for (const message of list.messages ?? []) {
        const recipients = (message.To ?? []).map(to => to.Address)
        if (!recipients.includes(email)) continue
        const detail = await (await fetch(`${MAILPIT}/message/${message.ID}`)).json()
        const text = `${detail.Text ?? ''}\n${detail.HTML ?? ''}\n${detail.Snippet ?? ''}`
        const match = text.match(/token=([A-Za-z0-9_-]+)/)
        if (match) return match[1]
      }
    } catch {
      // mailpit not ready yet
    }
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  throw new Error(`verification email for ${email} not found`)
}

async function ensureUser(email, nickname) {
  const registration = await request('/auth/register', {
    method: 'POST',
    body: { email, nickname, password: PASSWORD }
  })
  if (registration.status === 201) {
    const token = await findVerificationToken(email)
    const verify = await request('/auth/verify-email', {
      method: 'POST',
      body: { token }
    })
    if (verify.status !== 200) {
      throw new Error(`verify failed for ${email}: ${verify.text}`)
    }
  } else if (registration.status !== 409) {
    throw new Error(`register failed for ${email}: ${registration.status} ${registration.text}`)
  }

  const login = await request('/auth/login', {
    method: 'POST',
    body: { email, password: PASSWORD }
  })
  if (login.status !== 200) {
    throw new Error(`login failed for ${email}: ${login.status} ${login.text}`)
  }
  return {
    email,
    nickname,
    id: login.json.data.user.id,
    token: login.json.data.accessToken
  }
}

async function createTopic(author, categoryId, title, content, tags) {
  const result = await request('/topics', {
    method: 'POST',
    token: author.token,
    body: { categoryId, title, content, tags }
  })
  if (result.status !== 201) {
    throw new Error(`create topic failed: ${result.status} ${result.text}`)
  }
  return result.json.data
}

async function reply(author, topicId, content, replyToPostId = null) {
  const result = await request(`/topics/${topicId}/posts`, {
    method: 'POST',
    token: author.token,
    body: { content, ...(replyToPostId ? { replyToPostId } : {}) }
  })
  if (result.status !== 201) {
    throw new Error(`reply failed: ${result.status} ${result.text}`)
  }
  return result.json.data
}

async function main() {
  const alice = await ensureUser('demo-lance-a@example.com', '兰斯的旅人')
  const bella = await ensureUser('demo-lance-b@example.com', '希露的粉丝')
  const carol = await ensureUser('demo-lance-c@example.com', '攻略组小林')

  const categories = (await request('/categories')).json.data
  const flat = categories.flatMap(category => [category, ...category.children])
  const reviews = flat.find(category => category.slug === 'reviews')
  if (!reviews) throw new Error('reviews category missing')

  const title = `《兰斯10》两周通关记录：从自由都市到最终决战（${new Date().toLocaleDateString('zh-CN')}）`
  const topic = await createTopic(alice, reviews.id, title, `用了两周时间把《兰斯10》打通了，记录一下路线和几个容易错过的点。

![两周通关路线](/demo/lance-route.svg)

前期本来想在自由都市多刷一会，结果发现支线经验性价比不高，建议清完关键支线就走。利萨斯阶段记得把队伍里的治疗位换上来，不然后面爬塔续航会非常痛苦。

最终战前的队伍编成如下，兰斯主输出、希露治疗、雅典娜法术收尾：

![最终决战前的队伍编成](/demo/lance-party.svg)

几个容易错过的点：
1. 赛斯爬塔前把 MP 药补满，最终战第三阶段会连续放 AOE；
2. 赫尔曼收尾前留一个手动存档，方便回头补支线；
3. 如果追求全收集，自由都市阶段别急着推进主线。`, ['兰斯10', '通关感想'])

  const firstPost = topic.posts.items[0]

  await request(`/topics/${topic.id}/follow`, { method: 'POST', token: bella.token })
  await reply(bella, topic.id, `同感，自由都市那段我也刷久了。补充一个点：赛斯爬塔前把 MP 药补满，最终战第三阶段会连续放 AOE。

我最后一场的界面大概是这样：

![最终决战第三阶段](/demo/lance-battle.svg)`, firstPost.id)
  await reply(carol, topic.id, '感谢路线图，正好准备开二周目。想问一下赫尔曼收尾阶段有没有必须保留的存档点？')
  await request(`/posts/${firstPost.id}/upvote`, { method: 'POST', token: bella.token })
  await request(`/posts/${firstPost.id}/upvote`, { method: 'POST', token: carol.token })

  console.log(`topic id: ${topic.id}`)
  console.log(`open: http://localhost:3001/topics/${topic.id}`)
  console.log(`users: ${alice.nickname} / ${bella.nickname} / ${carol.nickname}`)
}

main().catch(error => {
  console.error(error)
  process.exit(1)
})
