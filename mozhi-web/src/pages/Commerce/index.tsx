import { Link } from "react-router-dom";

import { useCommerceWorkspaceQuery } from "@/hooks/useCommerceWorkspaceQuery";

export default function CommercePage() {
  const { data } = useCommerceWorkspaceQuery();

  if (!data) {
    return null;
  }

  return (
    <>
      <section className="mozhi-commerce-hero">
        <div className="mozhi-commerce-hero-copy">
          <h1 className="mozhi-commerce-title">{data.hero.title}</h1>
          <p className="mozhi-commerce-description">{data.hero.description}</p>
        </div>
        <article className="mozhi-commerce-hero-card">
          <span className="mozhi-home-card-eyebrow">当前焦点商品</span>
          <strong>{data.offers[0].title}</strong>
          <p>{data.offers[0].meta}</p>
        </article>
      </section>

      <section className="mozhi-commerce-layout">
        <div className="mozhi-commerce-main">
          <div className="mozhi-commerce-offer-grid">
            {data.offers.map((offer) => (
              <article key={offer.title} className="mozhi-commerce-offer-card">
                <span className="mozhi-home-card-eyebrow">精选商品</span>
                <h2>{offer.title}</h2>
                <p>{offer.description}</p>
                <div className="mozhi-commerce-offer-footer">
                  <span>{offer.meta}</span>
                  <button className="mozhi-commerce-offer-button" type="button">
                    {offer.action}
                  </button>
                </div>
              </article>
            ))}
          </div>

          <section className="mozhi-commerce-stage-card">
            <span className="mozhi-home-card-eyebrow">交易顺序</span>
            <div className="mozhi-commerce-stage-list">
              {data.stages.map((stage, index) => (
                <article key={stage.title} className="mozhi-commerce-stage-item">
                  <span className="mozhi-commerce-stage-step">0{index + 1}</span>
                  <div>
                    <h3>{stage.title}</h3>
                    <p>{stage.description}</p>
                  </div>
                </article>
              ))}
            </div>
          </section>
        </div>

        <aside className="mozhi-commerce-side">
          <section className="mozhi-commerce-panel">
            <span className="mozhi-home-card-eyebrow">商业服务面</span>
            <div className="mozhi-commerce-panel-list">
              {data.servicePanels.map((panel) => (
                <article key={panel.title} className="mozhi-commerce-panel-item">
                  <strong>{panel.title}</strong>
                  <p>{panel.description}</p>
                </article>
              ))}
            </div>
          </section>

          <section className="mozhi-commerce-panel">
            <span className="mozhi-home-card-eyebrow">继续动作</span>
            <div className="mozhi-commerce-actions">
              {data.quickActions.map((route) => (
                <Link key={route.label} className="mozhi-commerce-action-link" to={route.href}>
                  {route.label}
                </Link>
              ))}
            </div>
          </section>
        </aside>
      </section>
    </>
  );
}
