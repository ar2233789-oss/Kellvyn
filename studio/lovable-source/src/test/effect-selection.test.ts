import { describe, expect, it } from "vitest";
import { DEFAULT_SETTINGS, normalizeEffects, toggleEffectSelection } from "../components/kellvyn/store";

describe("visual effect selection", () => {
  it("keeps the original default of rain", () => {
    expect(DEFAULT_SETTINGS.effect).toEqual(["chuva"]);
  });

  it("migrates an existing saved single effect", () => {
    expect(normalizeEffects("chuva")).toEqual(["chuva"]);
    expect(normalizeEffects("raios")).toEqual(["raios"]);
  });

  it("allows rain and lightning to be active together", () => {
    expect(toggleEffectSelection(["chuva"], "raios")).toEqual(["chuva", "raios"]);
  });

  it("can turn off one effect without turning the other off", () => {
    expect(toggleEffectSelection(["chuva", "raios"], "raios")).toEqual(["chuva"]);
  });

  it("selecting Nenhum clears every active effect", () => {
    expect(toggleEffectSelection(["chuva", "raios"], "nenhum")).toEqual(["nenhum"]);
  });

  it("selecting an effect after Nenhum starts with only that effect", () => {
    expect(toggleEffectSelection(["nenhum"], "neve")).toEqual(["neve"]);
  });

  it("uses Nenhum when the last active effect is turned off", () => {
    expect(toggleEffectSelection(["chuva"], "chuva")).toEqual(["nenhum"]);
  });

  it("deduplicates restored settings and keeps Nenhum exclusive", () => {
    expect(normalizeEffects(["chuva", "chuva", "raios"])).toEqual(["chuva", "raios"]);
    expect(normalizeEffects(["chuva", "nenhum", "raios"])).toEqual(["nenhum"]);
  });
});
