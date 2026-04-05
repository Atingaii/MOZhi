import { API_BASE_URL } from "@/api/client";
import { useHealthStatusQuery } from "@/hooks/useHealthStatusQuery";

function formatCheckedAt(checkedAt: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "medium"
  }).format(new Date(checkedAt));
}

function buildDocumentationUrl(documentationUrl: string) {
  return new URL(documentationUrl, API_BASE_URL).toString();
}

export default function HomePage() {
  const { data, isLoading, isError, error } = useHealthStatusQuery();

  return (
    <section className="w-full rounded-2xl border border-line bg-surface p-8">
      <h1 className="text-2xl font-semibold">首页骨架</h1>
      <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-600">
        Feed、推荐流、热点内容和右侧栏位将在后续业务实现阶段接入。
      </p>
      <div className="mt-6 rounded-2xl border border-slate-200 bg-slate-50 p-5">
        <h2 className="text-base font-semibold text-slate-900">前后端联调状态</h2>
        {isLoading ? (
          <p className="mt-3 text-sm text-slate-600">正在连接后端健康检查接口...</p>
        ) : null}
        {isError ? (
          <p className="mt-3 text-sm text-rose-600">
            健康检查失败：{error instanceof Error ? error.message : "unknown error"}
          </p>
        ) : null}
        {data ? (
          <dl className="mt-4 grid gap-3 text-sm text-slate-700 sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">服务</dt>
              <dd className="mt-1 font-medium text-slate-900">{data.application}</dd>
            </div>
            <div>
              <dt className="text-slate-500">状态</dt>
              <dd className="mt-1 font-medium text-emerald-600">{data.status}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Profile</dt>
              <dd className="mt-1 font-medium text-slate-900">{data.profile}</dd>
            </div>
            <div>
              <dt className="text-slate-500">检查时间</dt>
              <dd className="mt-1 font-medium text-slate-900">{formatCheckedAt(data.checkedAt)}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-slate-500">文档入口</dt>
              <dd className="mt-1">
                <a
                  className="font-medium text-sky-600 transition hover:text-sky-500"
                  href={buildDocumentationUrl(data.documentationUrl)}
                  rel="noreferrer"
                  target="_blank"
                >
                  {data.documentationUrl}
                </a>
              </dd>
            </div>
          </dl>
        ) : null}
      </div>
    </section>
  );
}
