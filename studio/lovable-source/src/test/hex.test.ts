import { describe, it, expect } from "vitest";
import { normalizeHex } from "@/components/kellvyn/store";

describe("normalizeHex", () => {
  it("accepts 6 digits with #", () => expect(normalizeHex("#38e6bc")).toBe("#38E6BC"));
  it("accepts 6 digits without #", () => expect(normalizeHex("ff7a45")).toBe("#FF7A45"));
  it("rejects 3 digits", () => expect(normalizeHex("#fff")).toBeNull());
  it("rejects invalid chars", () => expect(normalizeHex("#GG0000")).toBeNull());
});
