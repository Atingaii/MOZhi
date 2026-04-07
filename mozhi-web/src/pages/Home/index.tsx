import { useState } from "react";
import { Link } from "react-router-dom";

import type { HomeFeedTab, HomePageDTO } from "@/api/modules/home";
import { PageSection, SectionHeading } from "@/components/ui/Editorial";
import { useHomePageQuery } from "@/hooks/useHomePageQuery";
import { useAuthStore } from "@/stores/useAuthStore";

function LandingHome({ landing }: { landing: HomePageDTO["landing"] }) {
  return (
    <>
      <section className="mozhi-home-landing-hero">
        <div className="mozhi-home-landing-copy">
          <h1 className="mozhi-home-landing-title">{landing.title}</h1>
          <p className="mozhi-home-landing-description">
            {landing.description}
          </p>
          <div className="mozhi-home-actions">
            <Link className="mozhi-cta-primary" to={landing.primaryAction.href}>
              {landing.primaryAction.label}
            </Link>
            <Link className="mozhi-cta-secondary" to={landing.secondaryAction.href}>
              {landing.secondaryAction.label}
            </Link>
          </div>
        </div>

        <div className="mozhi-home-preview">
          <div className="mozhi-home-preview-card mozhi-home-preview-feature">
            <span className="mozhi-home-preview-label">{landing.previewFeature.label}</span>
            <h2>{landing.previewFeature.title}</h2>
            <p>{landing.previewFeature.description}</p>
            <div className="mozhi-home-preview-meta">
              {landing.previewFeature.meta.map((item) => (
                <span key={item}>{item}</span>
              ))}
            </div>
          </div>
          <div className="mozhi-home-preview-stack">
            {landing.previewCards.map((card) => (
              <div key={card.title} className="mozhi-home-preview-card">
                <span className="mozhi-home-preview-label">{card.label}</span>
                <strong>{card.title}</strong>
                <span>{card.meta}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <PageSection>
        <SectionHeading
          title="本周精选"
          subtitle="首页先把最值得看的内容摆出来，而不是先给用户一张功能地图。"
        />
        <div className="mozhi-home-shelf-grid">
          {landing.picks.map((pick) => (
            <Link key={pick.title} className="mozhi-home-story-card" to={pick.href}>
              <span className="mozhi-home-card-eyebrow">{pick.category}</span>
              <h3 className="mozhi-home-story-title">{pick.title}</h3>
              <p className="mozhi-home-story-summary">{pick.summary}</p>
              <div className="mozhi-home-story-footer">
                <div>
                  <span className="mozhi-home-story-author">{pick.author}</span>
                  <span className="mozhi-home-story-role">{pick.role}</span>
                </div>
                <span className="mozhi-home-story-meta">{pick.meta}</span>
              </div>
            </Link>
          ))}
        </div>
      </PageSection>

      <PageSection>
        <SectionHeading
          title="热门讨论"
          subtitle="不要告诉用户“这里有问答”，直接给他们看现在最值得点进去的问题。"
        />
        <div className="mozhi-home-discussion-grid">
          {landing.discussions.map((discussion) => (
            <article key={discussion.title} className="mozhi-home-discussion-card">
              <span className="mozhi-home-card-eyebrow">正在发生</span>
              <h3 className="mozhi-home-discussion-title">{discussion.title}</h3>
              <div className="mozhi-home-discussion-meta">
                <span>{discussion.answers}</span>
                <span>{discussion.activity}</span>
              </div>
            </article>
          ))}
        </div>
      </PageSection>

      <PageSection>
        <SectionHeading
          title="活跃创作者"
          subtitle="内容平台的灵魂是人。首页应该让用户先看到值得追踪的作者和他们的关注方向。"
        />
        <div className="mozhi-home-creator-row" role="list" aria-label="活跃创作者">
          {landing.creators.map((creator) => (
            <article key={creator.name} className="mozhi-home-creator-card" role="listitem">
              <span className="mozhi-home-creator-avatar" style={{ background: creator.tone }}>
                {creator.name.slice(0, 1)}
              </span>
              <div className="mozhi-home-creator-copy">
                <strong>{creator.name}</strong>
                <span>{creator.focus}</span>
              </div>
            </article>
          ))}
        </div>
      </PageSection>

      <PageSection>
        <SectionHeading
          title="平台特色"
          subtitle="这不是功能清单，而是解释 MOZhi 为什么和普通内容站点不一样。"
        />
        <div className="mozhi-home-bento-grid">
          {landing.features.map((feature) => (
            <Link key={feature.title} className="mozhi-home-bento-card" to={feature.href}>
              <span className="mozhi-home-card-eyebrow">{feature.eyebrow}</span>
              <h3 className="mozhi-home-bento-title">{feature.title}</h3>
              <p className="mozhi-home-bento-description">{feature.description}</p>
            </Link>
          ))}
        </div>
      </PageSection>

      <PageSection>
        <div className="mozhi-home-proof-block">
          <div className="mozhi-home-proof-copy">
            <span className="mozhi-home-card-eyebrow">{landing.proof.eyebrow}</span>
            <h2>{landing.proof.title}</h2>
            <p>{landing.proof.description}</p>
            <div className="mozhi-home-proof-actions">
              <Link className="mozhi-cta-primary" to={landing.proof.primaryAction.href}>
                {landing.proof.primaryAction.label}
              </Link>
              <Link className="mozhi-cta-tertiary" to={landing.proof.secondaryAction.href}>
                {landing.proof.secondaryAction.label}
              </Link>
            </div>
          </div>
          <div className="mozhi-home-proof-side">
            <div className="mozhi-home-proof-metrics">
              {landing.proof.metrics.map((metric) => (
                <div key={metric.label} className="mozhi-home-proof-metric">
                  <span>{metric.label}</span>
                  <strong>{metric.value}</strong>
                </div>
              ))}
            </div>
            <div className="mozhi-home-proof-quotes">
              {landing.proof.quotes.map((quote) => (
                <blockquote key={quote} className="mozhi-home-proof-quote">
                  {quote}
                </blockquote>
              ))}
            </div>
          </div>
        </div>
      </PageSection>
    </>
  );
}

function AuthenticatedHome({ feed }: { feed: HomePageDTO["feed"] }) {
  const [activeFeed, setActiveFeed] = useState<HomeFeedTab>("following");
  const activeCards = feed.cards[activeFeed];

  return (
    <>
      <section className="mozhi-home-feed-rail" aria-label="快捷更新">
        {feed.rail.map((update) => (
          <article key={update.title} className="mozhi-home-feed-rail-card">
            <span className="mozhi-home-card-eyebrow">{update.title}</span>
            <strong>{update.detail}</strong>
          </article>
        ))}
      </section>

      <section className="mozhi-home-feed-layout">
        <div className="mozhi-home-feed-main">
          <div className="mozhi-home-feed-header">
            <div>
              <h1 className="mozhi-home-feed-title">{feed.title}</h1>
            </div>
            <div className="mozhi-home-feed-tabs" role="tablist" aria-label="内容流切换">
              {feed.tabs.map((tab) => (
                <button
                  key={tab.id}
                  aria-selected={activeFeed === tab.id}
                  className={`mozhi-home-feed-tab${activeFeed === tab.id ? " mozhi-home-feed-tab-active" : ""}`}
                  onClick={() => setActiveFeed(tab.id)}
                  role="tab"
                  type="button"
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          <div className="mozhi-home-feed-cards">
            {activeCards.map((card) => (
              <article key={card.title} className="mozhi-home-feed-card">
                <div className="mozhi-home-feed-card-header">
                  <div>
                    <span className="mozhi-home-feed-author">{card.author}</span>
                    <span className="mozhi-home-feed-role">{card.role}</span>
                  </div>
                  <span className="mozhi-home-feed-badge">{card.badge}</span>
                </div>
                <h2 className="mozhi-home-feed-card-title">{card.title}</h2>
                <p className="mozhi-home-feed-card-summary">{card.summary}</p>
                <span className="mozhi-home-feed-card-meta">{card.meta}</span>
              </article>
            ))}
          </div>
        </div>

        <aside className="mozhi-home-feed-side">
          <section className="mozhi-home-feed-panel">
            <span className="mozhi-home-card-eyebrow">热门话题</span>
            <div className="mozhi-home-feed-list">
              {feed.topics.map((topic) => (
                <div key={topic} className="mozhi-home-feed-list-item">
                  {topic}
                </div>
              ))}
            </div>
          </section>

          <section className="mozhi-home-feed-panel">
            <span className="mozhi-home-card-eyebrow">推荐关注</span>
            <div className="mozhi-home-feed-creators">
              {feed.creators.map((creator) => (
                <article key={creator.name} className="mozhi-home-feed-creator">
                  <div className="mozhi-home-feed-creator-copy">
                    <strong>{creator.name}</strong>
                    <span>{creator.label}</span>
                    <p>{creator.intro}</p>
                  </div>
                  <button className="mozhi-home-follow-button" type="button">
                    关注
                  </button>
                </article>
              ))}
            </div>
          </section>

          <section className="mozhi-home-feed-panel">
            <span className="mozhi-home-card-eyebrow">快捷操作</span>
            <div className="mozhi-home-feed-actions">
              {feed.actions.map((action) => (
                <Link key={action.label} className="mozhi-home-feed-action" to={action.href}>
                  {action.label}
                </Link>
              ))}
            </div>
          </section>
        </aside>
      </section>
    </>
  );
}

export default function HomePage() {
  const { status } = useAuthStore();
  const { data } = useHomePageQuery();

  if (!data) {
    return null;
  }

  return status === "authenticated" ? <AuthenticatedHome feed={data.feed} /> : <LandingHome landing={data.landing} />;
}
