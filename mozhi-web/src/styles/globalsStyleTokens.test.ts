import { readFileSync } from "node:fs";
import path from "node:path";

import { describe, expect, it } from "vitest";

const globalsCssPath = path.resolve(import.meta.dirname, "globals.css");
const globalsCss = readFileSync(globalsCssPath, "utf8");

describe("globals clarity tuning", () => {
  it("removes the old frosted navbar treatment", () => {
    expect(globalsCss).not.toContain("backdrop-filter: blur(12px);");
    expect(globalsCss).toContain("--nav-bg: rgba(250, 250, 250, 0.96);");
  });

  it("uses solid core surfaces instead of transparent card backgrounds", () => {
    expect(globalsCss).not.toContain("background: color-mix(in srgb, var(--bg-card) 92%, transparent);");
    expect(globalsCss).toContain("--surface-raised: #ffffff;");
    expect(globalsCss).toContain("--surface-muted: #fcfcfd;");
  });

  it("keeps the auth route shell vertically centered on desktop", () => {
    expect(globalsCss).toMatch(/\.mozhi-auth-route-main\s*\{[^}]*display:\s*flex;/s);
    expect(globalsCss).toMatch(/\.mozhi-auth-route-main\s*\{[^}]*align-items:\s*center;/s);
    expect(globalsCss).toMatch(/\.mozhi-auth-page-container\s*\{[^}]*margin:\s*0 auto;/s);
  });
});
