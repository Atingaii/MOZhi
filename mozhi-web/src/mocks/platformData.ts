export type SearchLane = "all" | "content" | "qa" | "commerce";

export interface SearchResult {
  readonly kind: Exclude<SearchLane, "all">;
  readonly title: string;
  readonly summary: string;
  readonly author: string;
  readonly meta: string;
  readonly href: string;
  readonly action: string;
}

export interface DiscussionThread {
  readonly id: string;
  readonly title: string;
  readonly summary: string;
  readonly answers: string[];
  readonly sourceTitle: string;
  readonly sourceExcerpt: string;
  readonly meta: string;
  readonly nextStep: string;
}

export interface DraftItem {
  readonly id: string;
  readonly title: string;
  readonly excerpt: string;
  readonly stage: string;
  readonly updatedAt: string;
  readonly blocks: string[];
  readonly checklist: string[];
}

export const landingPicks = [
  {
    category: "本周精选",
    title: "AI 写作工具不会替代作者，但会重新定义编辑台的工作流。",
    author: "叶川",
    role: "创作者研究",
    summary: "从草稿、批注到分发复盘，拆解创作系统应该如何服务真正长期写作的人。",
    meta: "12 分钟阅读",
    href: "/search"
  },
  {
    category: "深度长文",
    title: "为什么内容平台的问答区，应该贴着文章长出来而不是另起聊天室。",
    author: "林夏",
    role: "知识产品设计",
    summary: "围绕上下文引用、追问结构和作者回应节奏，重做问答系统的产品边界。",
    meta: "9 个观点节点",
    href: "/qa"
  },
  {
    category: "商业观察",
    title: "把团购和内容放在同一个站点里，怎样才不会破坏阅读体验？",
    author: "周谨",
    role: "社区商业",
    summary: "从首页货架、信任建立到转化入口的时机，把内容和交易重新排进一条叙事。",
    meta: "附策略样例",
    href: "/commerce"
  }
] as const;

export const landingDiscussions = [
  {
    title: "做一个真正可引用的问答区，最先应该牺牲什么？",
    answers: "18 条回答",
    activity: "12 分钟前仍在更新"
  },
  {
    title: "创作者首页到底该展示作品，还是先展示人格和主张？",
    answers: "26 条讨论",
    activity: "3 位作者刚加入"
  },
  {
    title: "知识产品、订阅和商城入口，哪个应该先出现？",
    answers: "11 条拆解",
    activity: "今天新增 4 条追问"
  }
] as const;

export const landingCreators = [
  { name: "叶川", focus: "AI 写作 / 内容系统", tone: "#2563eb" },
  { name: "林夏", focus: "知识产品 / 社区设计", tone: "#0f766e" },
  { name: "周谨", focus: "商业叙事 / 团购策略", tone: "#c2410c" },
  { name: "沈鹿", focus: "创作工具 / 编辑流程", tone: "#7c3aed" },
  { name: "宋河", focus: "内容运营 / 社区增长", tone: "#db2777" },
  { name: "何见", focus: "产品批评 / 阅读体验", tone: "#475569" }
] as const;

export const landingFeatures = [
  {
    title: "知识产品",
    eyebrow: "Monetize",
    description: "把文章、卡片和课程整理成可售卖的知识组合，不需要跳出平台。",
    href: "/commerce"
  },
  {
    title: "团购大厅",
    eyebrow: "Commerce",
    description: "让读者围绕一位作者、一组内容或一个知识产品快速结成购买关系。",
    href: "/commerce"
  },
  {
    title: "创作台",
    eyebrow: "Creator studio",
    description: "从选题、草稿、模块到发布复盘，在同一套工作台里闭环。",
    href: "/editor"
  },
  {
    title: "问答引擎",
    eyebrow: "Contextual Q&A",
    description: "问题不再漂在空中，而是总能回到原始内容、引用段落和作者语境。",
    href: "/qa"
  }
] as const;

export const proofMetrics = [
  { label: "精选内容策展位", value: "每周更新" },
  { label: "讨论区信号", value: "今日 55 条新追问" },
  { label: "创作者橱窗", value: "24 位活跃作者" }
] as const;

export const proofQuotes = [
  "“先看到真内容，再决定要不要留下来，这才像一个可信的内容平台。”",
  "“如果问答、创作和变现能在同一条路径里，我会更愿意持续经营。”"
] as const;

export const feedTabs = [
  { id: "following", label: "关注" },
  { id: "discover", label: "发现" }
] as const;

export const followingFeed = [
  {
    author: "叶川",
    role: "创作者研究",
    title: "我把首页改版拆成了 4 个模块，现在终于能解释“为什么用户要留下来”。",
    summary: "从 Hero 到内容货架，再到创作者展示和社会证明，整理一份对内容平台更有效的首页框架。",
    badge: "新发布",
    meta: "16 分钟前 · 8 分钟阅读"
  },
  {
    author: "林夏",
    role: "知识产品设计",
    title: "问答如果不挂靠文章上下文，很快就会变成没有记忆的聊天区。",
    summary: "这篇稿子给出了引用块、追问树和作者回应位的结构草图，也解释了为什么要避免空白输入框。",
    badge: "关注作者更新",
    meta: "43 分钟前 · 5 条评论"
  },
  {
    author: "周谨",
    role: "社区商业",
    title: "把商城放进内容站，不等于让每一个页面都像货架。",
    summary: "商业入口应该出现在信任建立之后，所以首页和内容页的商业位置必须严格克制。",
    badge: "专题续写",
    meta: "今天 09:40 · 收藏 126"
  }
] as const;

export const discoverFeed = [
  {
    author: "沈鹿",
    role: "创作工具",
    title: "如果创作台要长期可用，就不能只是一个漂亮的输入框。",
    summary: "从草稿状态、模块插入和发布回看三件事出发，重做创作者工作台的节奏。",
    badge: "推荐阅读",
    meta: "刚刚推荐 · 11 分钟阅读"
  },
  {
    author: "宋河",
    role: "社区增长",
    title: "真正让人愿意注册的，不是 CTA 文案，而是首页上有没有真实的好东西。",
    summary: "把 Product Hunt、Substack 和 Medium 的首页策略抽出来，重新比较内容、人物和讨论的先后顺序。",
    badge: "今日发现",
    meta: "今日热度 92"
  },
  {
    author: "何见",
    role: "阅读体验",
    title: "信息流要高效，但不能像冷冰冰的数据库列表。",
    summary: "一个好的已登录首页，应该在效率和编辑感之间找到平衡，既能看新内容，也能感知社区温度。",
    badge: "编辑推荐",
    meta: "2 小时前 · 3 个引用"
  }
] as const;

export const feedUpdates = [
  { title: "关注作者更新", detail: "叶川发布了新文章" },
  { title: "问答有新回复", detail: "2 个问题收到追问" },
  { title: "商品动态", detail: "知识产品新增 1 次团购" }
] as const;

export const feedTopics = [
  "创作者首页应该先展示什么？",
  "上下文问答和普通聊天的分界线在哪？",
  "内容平台如何放商业入口才不打扰阅读？",
  "什么样的精选货架会让陌生用户愿意留下来？",
  "创作台是不是应该承担发布后的复盘？"
] as const;

export const feedCreators = [
  {
    name: "温知",
    intro: "擅长把长文拆成结构化知识卡片。",
    label: "知识策展"
  },
  {
    name: "顾北",
    intro: "研究社区如何承接内容后的追问和关系。",
    label: "社区设计"
  },
  {
    name: "阿笙",
    intro: "持续写产品、创作与变现之间的桥接案例。",
    label: "产品写作"
  }
] as const;

export const homeQuickActions = [
  { label: "写新文章", href: "/editor" },
  { label: "发起问答", href: "/qa" },
  { label: "管理商品", href: "/commerce" }
] as const;

export const searchLaneTabs: Array<{ readonly id: SearchLane; readonly label: string }> = [
  { id: "all", label: "全部" },
  { id: "content", label: "内容" },
  { id: "qa", label: "问答" },
  { id: "commerce", label: "商品" }
];

export const trendingQueries = [
  "内容平台首页方法论",
  "上下文问答",
  "创作者工作流",
  "知识产品定价",
  "团购大厅"
] as const;

export const searchResults: SearchResult[] = [
  {
    kind: "content",
    title: "AI 写作工具不会替代作者，但会重新定义编辑台的工作流。",
    summary: "从草稿、批注、发布到复盘，把创作台拆成真正适合长期写作者使用的四个阶段。",
    author: "叶川",
    meta: "12 分钟阅读 · 精选内容",
    href: "/editor",
    action: "查看文章"
  },
  {
    kind: "qa",
    title: "为什么问答区必须贴着文章上下文，而不能做成纯聊天区？",
    summary: "围绕引用段落、追问树和作者回应，解释什么样的问答结构才会有记忆和信任。",
    author: "林夏",
    meta: "18 条回答 · 今日最热",
    href: "/qa",
    action: "进入讨论"
  },
  {
    kind: "commerce",
    title: "知识产品《内容平台首页方法论》团购中，还差 4 人成团。",
    summary: "把首页、精选货架和创作者引导整合成一套真正可落地的页面框架。",
    author: "周谨",
    meta: "团购大厅 · 限时进行中",
    href: "/commerce",
    action: "查看商品"
  },
  {
    kind: "content",
    title: "如果创作者首页只展示作品，会损失掉什么？",
    summary: "创作者介绍、栏目主张和作品货架应该如何排序，决定了陌生用户会不会继续往下读。",
    author: "何见",
    meta: "8 分钟阅读 · 编辑推荐",
    href: "/search",
    action: "继续浏览"
  },
  {
    kind: "qa",
    title: "内容、社区和商业化到底应该先出现哪一个？",
    summary: "从信任建立的顺序出发，讨论首页、内容页和交易页分别应该承担什么任务。",
    author: "顾北",
    meta: "26 条追问 · 正在更新",
    href: "/qa",
    action: "查看回答"
  },
  {
    kind: "commerce",
    title: "把课程、卡片和订阅做成一个知识组合，该怎么包装？",
    summary: "不是所有知识产品都应该单卖，组合包装能更好承接内容消费后的购买欲望。",
    author: "温知",
    meta: "知识产品 · 新上架",
    href: "/commerce",
    action: "打开商品页"
  }
];

export const discoveryPanels = [
  {
    title: "搜索不是数据库窗口",
    description: "结果页要把内容、问答和商品排进同一条发现路径里，而不是让用户自己切页寻找。"
  },
  {
    title: "每个结果都应该带下一步动作",
    description: "找到一篇文章后可以继续提问，找到一个商品后可以回看上下文，这才叫桥接。"
  }
] as const;

export const searchQuickRoutes = [
  { label: "打开问答区", href: "/qa" },
  { label: "进入创作台", href: "/editor" },
  { label: "查看商城", href: "/commerce" }
] as const;

export const qaThreads: DiscussionThread[] = [
  {
    id: "context-first",
    title: "为什么问答区必须贴着文章上下文，而不能做成纯聊天区？",
    summary: "如果问题脱离内容来源，用户会很快失去判断依据，回答质量和信任都会一起下降。",
    answers: [
      "第一步不是生成答案，而是先告诉用户这个问题是从哪段内容里长出来的。上下文被看见，回答才有依据。",
      "第二步是保留追问路径。用户不是只想要一个结论，而是想知道这条结论沿着什么推理链路成立。",
      "第三步是给出继续动作，比如回到原文、转到搜索、或者把问题带回创作台，而不是把用户困在聊天框里。"
    ],
    sourceTitle: "《上下文问答不是聊天框》",
    sourceExcerpt: "高质量问答的前提不是模型更会说，而是它清楚自己引用了哪段内容、哪类证据，以及用户回答之后能去哪里。",
    meta: "18 条回答 · 12 分钟前更新",
    nextStep: "回到搜索查看相关文章"
  },
  {
    id: "creator-home",
    title: "创作者首页到底该展示作品，还是先展示人格和主张？",
    summary: "陌生用户进入作者主页时，先看到什么，会直接决定他要不要继续花时间阅读。",
    answers: [
      "如果首页完全是作品列表，陌生人看不到作者的角度，就很难建立持续关注的理由。",
      "如果首页只有人格和态度，却没有作品货架，用户会不知道这里到底产出了什么。",
      "更合理的做法是先给一句主张，再给一个精选内容货架，让“是谁”和“写了什么”同时成立。"
    ],
    sourceTitle: "《创作者首页排序策略》",
    sourceExcerpt: "作者主页的核心任务不是展示所有内容，而是在最短路径内建立“我为什么要继续关注你”的判断。",
    meta: "26 条追问 · 3 位作者参与",
    nextStep: "进入创作台调整作者主页"
  },
  {
    id: "commerce-entry",
    title: "内容平台如何放商业入口，才不会破坏阅读体验？",
    summary: "商业化一定要做，但它什么时候出现、以什么姿态出现，会决定平台到底像货架还是像内容站。",
    answers: [
      "商业入口不应该先于价值感出现。用户先看到内容，再看到作者，最后才轮到购买动作。",
      "最自然的方式是把商品嵌在内容关系里，比如“围绕这篇内容延伸出的知识产品”，而不是孤立广告位。",
      "如果一个商业入口不能解释自己为什么在这里，它就不该出现在当前页面。"
    ],
    sourceTitle: "《内容与商城的叙事顺序》",
    sourceExcerpt: "信任先于转化。先让用户理解他为什么愿意留下来，再决定何时邀请他购买。",
    meta: "11 条拆解 · 今日热议",
    nextStep: "查看团购大厅案例"
  }
];

export const qaQuickMoves = [
  { label: "回到搜索", href: "/search" },
  { label: "打开创作台", href: "/editor" },
  { label: "查看商城", href: "/commerce" }
] as const;

export const qaTrustSignals = [
  "回答必须显示它引用的来源和语境。",
  "每个讨论都应该能继续追问，而不是止于一句总结。",
  "问答完成后必须有可继续动作，不把用户困在当前视图。"
] as const;

export const editorDrafts: DraftItem[] = [
  {
    id: "homepage-system",
    title: "内容平台首页方法论与 MOZhi 视觉系统映射",
    excerpt: "把首页从品牌说明页推进到真实内容货架，顺便固定 Search、QA、Editor、Commerce 的页面叙事顺序。",
    stage: "主稿撰写中",
    updatedAt: "14 分钟前更新",
    blocks: ["Hero 改写", "精选货架", "活跃创作者", "注册转化"],
    checklist: ["补真实标题样本", "加入封面选择", "同步到设计稿"]
  },
  {
    id: "qa-context",
    title: "上下文问答为什么不能退化成聊天框",
    excerpt: "围绕来源引用、追问树和后续动作，重做讨论区的交互顺序和视觉结构。",
    stage: "结构已完成",
    updatedAt: "今天 09:48",
    blocks: ["来源卡", "追问路径", "动作区", "线程切换"],
    checklist: ["补作者回应位", "增加引用样式", "接真实问答数据"]
  },
  {
    id: "commerce-story",
    title: "内容平台里的商业入口，应该怎样排进阅读体验",
    excerpt: "把团购、知识产品和售后关系放进一条可信的内容叙事，而不是单纯促销。",
    stage: "等待发布",
    updatedAt: "昨天 18:20",
    blocks: ["专题导购", "团购进度", "信任说明", "售后回流"],
    checklist: ["补实际商品名", "接通知回流", "联动作者页"]
  }
];

export const editorRailNotes = [
  {
    title: "内容模块",
    description: "长文、摘要卡、知识节点和商品块都作为内容模块插入，而不是拆成四套工具。"
  },
  {
    title: "分发视图",
    description: "一篇内容会同步影响首页精选、搜索结果、问答引用和商城承接位。"
  },
  {
    title: "发布检查",
    description: "创作完成前必须过一遍封面、摘要、标签、来源、CTA 和后续动作。"
  }
] as const;

export const editorActionRoutes = [
  { label: "回到首页", href: "/" },
  { label: "查看问答工作台", href: "/qa" },
  { label: "进入搜索页", href: "/search" }
] as const;

export const featuredOffers = [
  {
    title: "知识产品《内容平台首页方法论》",
    description: "把内容货架、创作者橱窗、搜索发现和注册转化整合成一套真正可落地的页面框架。",
    meta: "团购进行中 · 还差 4 人成团",
    action: "查看商品"
  },
  {
    title: "专题专栏《上下文问答设计》",
    description: "围绕来源引用、追问树和后续动作，系统拆解为什么问答区不能退化成聊天框。",
    meta: "知识专栏 · 今日新增 12 位收藏",
    action: "阅读并购买"
  }
] as const;

export const commerceStages = [
  {
    title: "内容里出现商品",
    description: "商业入口应该由内容关系自然带出，而不是直接打断阅读。"
  },
  {
    title: "商品页解释为什么值得买",
    description: "价格、内容结构、适合谁，以及为什么现在应该行动，都要被讲清楚。"
  },
  {
    title: "成交后回流到内容与社区",
    description: "订单完成之后，用户仍然应该回到作者、讨论和后续更新中。"
  }
] as const;

export const commerceServicePanels = [
  {
    title: "团购大厅",
    description: "展示进行中的拼团、剩余人数和与内容的关联入口。"
  },
  {
    title: "知识产品",
    description: "把专栏、课程、卡片和数字包裹成更完整的知识组合。"
  },
  {
    title: "订单回流",
    description: "成交后用户会通过通知、更新和讨论继续留在内容系统里。"
  }
] as const;

export const commerceQuickActions = [
  { label: "回到首页", href: "/" },
  { label: "进入搜索页", href: "/search" },
  { label: "查看通知", href: "/notifications" }
] as const;
