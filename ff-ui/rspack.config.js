const path = require("path");

module.exports = {
  entry: "./src/index.tsx",
  devtool: "source-map",
  output: {
    filename: "index.js",
    path: path.resolve(__dirname, "build", "ui"),
    clean: true,
  },
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".jsx"],
    alias: {
      "react": "preact/compat",
      "react-dom": "preact/compat",
      "react/jsx-runtime": "preact/jsx-runtime",
      "@ui": path.resolve(__dirname, "./src")
    }
  },
  module: {
    rules: [
      {
        test: /\.(ts|tsx)$/,
        exclude: /node_modules/,
        use: {
          loader: "builtin:swc-loader",
          options: {
            jsc: {
              parser: {syntax: "typescript", tsx: true},
              transform: {react: {runtime: "automatic"}}
            }
          }
        }
      }
    ]
  }
};
