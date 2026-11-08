module.exports = {
  forbidden: [
    {
      name: "no-circular",
      severity: "error",
      from: { path: "^src/" },
      to: { circular: true },
    },
    {
      name: "shared-independent-of-features",
      severity: "error",
      from: { path: "^src/shared/" },
      to: { path: "^src/features/" },
    },
  ],
  options: { doNotFollow: { path: "node_modules" }, tsConfig: { fileName: "tsconfig.json" } },
};
