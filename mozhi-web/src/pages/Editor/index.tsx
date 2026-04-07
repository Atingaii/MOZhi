import { useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { editorWorkspaceSnapshot, type EditorDraftDTO } from "@/api/modules/editor";
import { useEditorWorkspaceQuery } from "@/hooks/useEditorWorkspaceQuery";

export default function EditorPage() {
  const { data } = useEditorWorkspaceQuery();
  const [activeDraftId, setActiveDraftId] = useState<EditorDraftDTO["id"]>(editorWorkspaceSnapshot.drafts[0].id);
  const workspace = data ?? editorWorkspaceSnapshot;

  const fallbackDraft = workspace.drafts[0];
  const activeDraft = useMemo(
    () => workspace.drafts.find((draft) => draft.id === activeDraftId) ?? fallbackDraft,
    [activeDraftId, fallbackDraft, workspace.drafts]
  );

  return (
    <>
      <section className="mozhi-editor-hero">
        <div className="mozhi-editor-hero-copy">
          <h1 className="mozhi-editor-title">{workspace.hero.title}</h1>
          <p className="mozhi-editor-description">{workspace.hero.description}</p>
        </div>
        <article className="mozhi-editor-hero-card">
          <span className="mozhi-home-card-eyebrow">当前主稿</span>
          <strong>{activeDraft.title}</strong>
          <p>
            {activeDraft.stage} · {activeDraft.updatedAt}
          </p>
        </article>
      </section>

      <section className="mozhi-editor-layout">
        <div className="mozhi-editor-main">
          <article className="mozhi-editor-canvas-card">
            <span className="mozhi-home-card-eyebrow">编辑画布</span>
            <h2>{activeDraft.title}</h2>
            <p>{activeDraft.excerpt}</p>
            <div className="mozhi-editor-blocks">
              {activeDraft.blocks.map((block) => (
                <span key={block} className="mozhi-editor-block-chip">
                  {block}
                </span>
              ))}
            </div>
          </article>

          <div className="mozhi-editor-rail-grid">
            {workspace.railNotes.map((note) => (
              <article key={note.title} className="mozhi-editor-note-card">
                <span className="mozhi-home-card-eyebrow">Rail</span>
                <h3>{note.title}</h3>
                <p>{note.description}</p>
              </article>
            ))}
          </div>

          <section className="mozhi-editor-checklist-card">
            <span className="mozhi-home-card-eyebrow">发布前检查</span>
            <div className="mozhi-editor-checklist">
              {activeDraft.checklist.map((item) => (
                <div key={item} className="mozhi-editor-check-item">
                  {item}
                </div>
              ))}
            </div>
          </section>
        </div>

        <aside className="mozhi-editor-side">
          <section className="mozhi-editor-panel">
            <span className="mozhi-home-card-eyebrow">草稿队列</span>
            <div className="mozhi-editor-draft-list" role="tablist" aria-label="草稿列表">
              {workspace.drafts.map((draft) => (
                <button
                  key={draft.id}
                  aria-selected={activeDraft.id === draft.id}
                  className={`mozhi-editor-draft-item${activeDraft.id === draft.id ? " mozhi-editor-draft-item-active" : ""}`}
                  onClick={() => setActiveDraftId(draft.id)}
                  role="tab"
                  type="button"
                >
                  <strong>{draft.title}</strong>
                  <p>{draft.excerpt}</p>
                  <span>
                    {draft.stage} · {draft.updatedAt}
                  </span>
                </button>
              ))}
            </div>
          </section>

          <section className="mozhi-editor-panel">
            <span className="mozhi-home-card-eyebrow">快捷动作</span>
            <div className="mozhi-editor-actions">
              {workspace.actionRoutes.map((route) => (
                <Link key={route.label} className="mozhi-editor-action-link" to={route.href}>
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
