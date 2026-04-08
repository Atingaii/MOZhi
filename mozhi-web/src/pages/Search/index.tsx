import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import { searchDiscoverySnapshot, type SearchLane } from "@/api/modules/search";
import { useSearchDiscoveryQuery } from "@/hooks/useSearchDiscoveryQuery";

function laneLabel(kind: Exclude<SearchLane, "all">) {
  switch (kind) {
    case "content":
      return "内容";
    case "qa":
      return "问答";
    case "commerce":
      return "商品";
  }
}

export default function SearchPage() {
  const { data } = useSearchDiscoveryQuery();
  const [searchParams] = useSearchParams();
  const routeQuery = searchParams.get("q");
  const [query, setQuery] = useState(routeQuery ?? searchDiscoverySnapshot.workspace.initialQuery);
  const [activeLane, setActiveLane] = useState<SearchLane>("all");
  const deferredQuery = useDeferredValue(query);

  useEffect(() => {
    setQuery(routeQuery ?? searchDiscoverySnapshot.workspace.initialQuery);
  }, [routeQuery]);

  const filteredResults = useMemo(() => {
    if (!data) {
      return [];
    }

    const normalizedQuery = deferredQuery.trim().toLowerCase();

    return data.workspace.results.filter((result) => {
      const laneMatch = activeLane === "all" || result.kind === activeLane;
      const queryMatch =
        normalizedQuery.length === 0 ||
        `${result.title} ${result.summary} ${result.author}`.toLowerCase().includes(normalizedQuery);

      return laneMatch && queryMatch;
    });
  }, [activeLane, data, deferredQuery]);

  if (!data) {
    return null;
  }

  return (
    <>
      <section className="mozhi-search-hero">
        <div className="mozhi-search-hero-copy">
          <h1 className="mozhi-search-title">{data.hero.title}</h1>
          <p className="mozhi-search-description">{data.hero.description}</p>
        </div>
        <div className="mozhi-search-hero-side">
          {data.hero.panels.map((panel) => (
            <article key={panel.title} className="mozhi-search-insight-card">
              <span className="mozhi-home-card-eyebrow">检索信号</span>
              <h2>{panel.title}</h2>
              <p>{panel.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="mozhi-search-workspace">
        <div className="mozhi-search-bar-shell">
          <label className="mozhi-search-input-wrap" htmlFor="mozhi-search-input">
            <span className="mozhi-home-card-eyebrow">Search query</span>
            <input
              id="mozhi-search-input"
              className="mozhi-search-input"
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索内容、问答、创作者或商品"
              type="search"
              value={query}
            />
          </label>
          <div className="mozhi-search-tabs" role="tablist" aria-label="搜索范围">
            {data.workspace.lanes.map((tab) => (
              <button
                key={tab.id}
                aria-selected={activeLane === tab.id}
                className={`mozhi-search-tab${activeLane === tab.id ? " mozhi-search-tab-active" : ""}`}
                onClick={() => setActiveLane(tab.id)}
                role="tab"
                type="button"
              >
                {tab.label}
              </button>
            ))}
          </div>
          <div className="mozhi-search-trending">
            {data.workspace.trendingQueries.map((topic) => (
              <button
                key={topic}
                className="mozhi-search-chip"
                onClick={() => setQuery(topic)}
                type="button"
              >
                {topic}
              </button>
            ))}
          </div>
        </div>

        <div className="mozhi-search-layout">
          <div className="mozhi-search-main">
            <div className="mozhi-search-results-head">
              <div>
                <span className="mozhi-home-card-eyebrow">{data.workspace.resultsTitle}</span>
                <h2>
                  {query ? `“${query}”` : "全部结果"} · 找到 {filteredResults.length} 条内容
                </h2>
              </div>
              <span className="mozhi-search-results-note">{data.workspace.resultsNote}</span>
            </div>

            <div className="mozhi-search-results-list">
              {filteredResults.length > 0 ? (
                filteredResults.map((result) => (
                  <article key={`${result.kind}-${result.title}`} className="mozhi-search-result-card">
                    <div className="mozhi-search-result-head">
                      <span className="mozhi-search-result-kind">{laneLabel(result.kind)}</span>
                      <span className="mozhi-search-result-meta">{result.meta}</span>
                    </div>
                    <h3 className="mozhi-search-result-title">{result.title}</h3>
                    <p className="mozhi-search-result-summary">{result.summary}</p>
                    <div className="mozhi-search-result-footer">
                      <span className="mozhi-search-result-author">{result.author}</span>
                      <Link className="mozhi-search-result-link" to={result.href}>
                        {result.action}
                      </Link>
                    </div>
                  </article>
                ))
              ) : (
                <article className="mozhi-search-empty-card">
                  <span className="mozhi-home-card-eyebrow">{data.workspace.emptyState.eyebrow}</span>
                  <h3>{data.workspace.emptyState.title}</h3>
                  <p>{data.workspace.emptyState.description}</p>
                </article>
              )}
            </div>
          </div>

          <aside className="mozhi-search-side">
            <section className="mozhi-search-panel">
              <span className="mozhi-home-card-eyebrow">继续探索</span>
              <div className="mozhi-search-panel-list">
                {data.workspace.trendingQueries.map((topic) => (
                  <button
                    key={`panel-${topic}`}
                    className="mozhi-search-panel-item"
                    onClick={() => setQuery(topic)}
                    type="button"
                  >
                    {topic}
                  </button>
                ))}
              </div>
            </section>

            <section className="mozhi-search-panel">
              <span className="mozhi-home-card-eyebrow">下一步动作</span>
              <div className="mozhi-search-panel-actions">
                {data.workspace.quickRoutes.map((route) => (
                  <Link key={route.label} className="mozhi-search-action-link" to={route.href}>
                    {route.label}
                  </Link>
                ))}
              </div>
            </section>
          </aside>
        </div>
      </section>
    </>
  );
}
