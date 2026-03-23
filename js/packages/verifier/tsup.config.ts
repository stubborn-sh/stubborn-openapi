import { defineConfig } from "tsup";

export default defineConfig({
  entry: ["src/index.ts"],
  format: ["esm", "cjs"],
  dts: false,
  clean: true,
  sourcemap: true,
  external: [
    "@stubborn/broker-client",
    "@stubborn/stub-server",
  ],
});
