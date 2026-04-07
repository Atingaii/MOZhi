import { useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { qaWorkspaceSnapshot, type QAThreadDTO } from "@/api/modules/qa";
import { useQAWorkspaceQuery } from "@/hooks/useQAWorkspaceQuery";

export default function QAPage() {
  const { data } = useQAWorkspaceQuery();
  const [activeThreadId, setActiveThreadId] = useState<QAThreadDTO["id"]>(qaWorkspaceSnapshot.threads[0].id);
  const workspace = data ?? qaWorkspaceSnapshot;

  const fallbackThread = workspace.threads[0];
  const activeThread = useMemo(
    () => workspace.threads.find((thread) => thread.id === activeThreadId) ?? fallbackThread,
    [activeThreadId, fallbackThread, workspace.threads]
  );

  return (
    <>
      <section className="mozhi-qa-hero">
        <div className="mozhi-qa-hero-copy">
          <h1 className="mozhi-qa-title">{workspace.hero.title}</h1>
          <p className="mozhi-qa-description">{workspace.hero.description}</p>
        </div>
        <div className="mozhi-qa-hero-aside">
          <article className="mozhi-qa-hero-card">
            <span className="mozhi-home-card-eyebrow">当前讨论焦点</span>
            <strong>{activeThread.title}</strong>
            <p>{activeThread.meta}</p>
          </article>
        </div>
      </section>

      <section className="mozhi-qa-layout">
        <div className="mozhi-qa-thread-list" role="tablist" aria-label="讨论线程">
          {workspace.threads.map((thread) => (
            <button
              key={thread.id}
              aria-selected={activeThread.id === thread.id}
              className={`mozhi-qa-thread-item${activeThread.id === thread.id ? " mozhi-qa-thread-item-active" : ""}`}
              onClick={() => setActiveThreadId(thread.id)}
              role="tab"
              type="button"
            >
              <span className="mozhi-home-card-eyebrow">讨论线程</span>
              <strong>{thread.title}</strong>
              <p>{thread.summary}</p>
              <span>{thread.meta}</span>
            </button>
          ))}
        </div>

        <div className="mozhi-qa-main">
          <article className="mozhi-qa-main-card">
            <span className="mozhi-home-card-eyebrow">Selected question</span>
            <h2>{activeThread.title}</h2>
            <p>{activeThread.summary}</p>
          </article>

          <div className="mozhi-qa-answer-list">
            {activeThread.answers.map((answer, index) => (
              <article key={`${activeThread.id}-${answer}`} className="mozhi-qa-answer-card">
                <span className="mozhi-qa-answer-step">0{index + 1}</span>
                <p>{answer}</p>
              </article>
            ))}
          </div>
        </div>

        <aside className="mozhi-qa-side">
          <section className="mozhi-qa-panel">
            <span className="mozhi-home-card-eyebrow">来源上下文</span>
            <h3>{activeThread.sourceTitle}</h3>
            <p>{activeThread.sourceExcerpt}</p>
          </section>

          <section className="mozhi-qa-panel">
            <span className="mozhi-home-card-eyebrow">可信度规则</span>
            <div className="mozhi-qa-signal-list">
              {workspace.trustSignals.map((signal) => (
                <div key={signal} className="mozhi-qa-signal-item">
                  {signal}
                </div>
              ))}
            </div>
          </section>

          <section className="mozhi-qa-panel">
            <span className="mozhi-home-card-eyebrow">继续动作</span>
            <div className="mozhi-qa-actions">
              {workspace.quickMoves.map((move) => (
                <Link key={move.label} className="mozhi-qa-action" to={move.href}>
                  {move.label}
                </Link>
              ))}
              <span className="mozhi-qa-next-step">{activeThread.nextStep}</span>
            </div>
          </section>
        </aside>
      </section>
    </>
  );
}
