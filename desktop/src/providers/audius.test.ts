import { describe, expect, it } from "vitest";
import { audiusStreamUrl, formatDuration } from "./audius";

describe("Audius provider helpers", () => {
  it("builds a stream URL with the app name", () => {
    const url = new URL(audiusStreamUrl("track_123"));
    expect(url.pathname).toBe("/v1/tracks/track_123/stream");
    expect(url.searchParams.get("app_name")).toBe("nuvora_desktop");
  });

  it("rejects malformed track identifiers", () => {
    expect(() => audiusStreamUrl("../secret")).toThrow("Invalid Audius track identifier");
  });

  it("formats duration safely", () => {
    expect(formatDuration(125)).toBe("2:05");
    expect(formatDuration(undefined)).toBe("--:--");
  });
});