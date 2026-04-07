const DEFAULT_MOCK_DELAY_MS = 120;

function createAbortError() {
  return new DOMException("The operation was aborted.", "AbortError");
}

export function resolveMockApiData<T>(
  data: T,
  signal?: AbortSignal,
  delayMs = DEFAULT_MOCK_DELAY_MS
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    if (signal?.aborted) {
      reject(createAbortError());
      return;
    }

    const timeoutId = globalThis.setTimeout(() => {
      signal?.removeEventListener("abort", handleAbort);
      resolve(data);
    }, delayMs);

    function handleAbort() {
      globalThis.clearTimeout(timeoutId);
      reject(createAbortError());
    }

    signal?.addEventListener("abort", handleAbort, { once: true });
  });
}
