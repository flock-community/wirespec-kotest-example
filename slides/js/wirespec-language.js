// A small highlight.js grammar for Wirespec (.ws), enough for the snippets in this deck
function wirespecLanguage(hljs) {
  const BUILT_IN = ["String", "Integer", "Number", "Boolean", "Unit", "Bytes"];

  return {
    name: "Wirespec",
    aliases: ["ws"],
    keywords: {
      keyword: ["type", "enum", "endpoint", "channel", "rpc", "part", "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE"],
      built_in: BUILT_IN,
    },
    contains: [
      hljs.C_BLOCK_COMMENT_MODE,
      hljs.C_LINE_COMMENT_MODE,
      // The regex of a refined type: String(/^97[89][0-9]{10}$/g)
      { scope: "regexp", begin: /(?<=\()\/(?![/*])/, end: /\/[a-z]*(?=\))/ },
      // Path segments: /books, /loan
      { scope: "string", match: /\/[a-z][a-z0-9-]*/ },
      // Field names: isbn:
      { scope: "attr", match: /\b[a-z][A-Za-z0-9]*(?=\s*:)/ },
      // Type names, but not the built-in types or ALL_CAPS methods and enum entries
      { scope: "type", match: new RegExp(`\\b(?!(?:${BUILT_IN.join("|")})\\b)[A-Z][a-z][A-Za-z0-9]*\\b`) },
      { scope: "number", match: /\b\d+(?:\.\d+)?\b/ },
    ],
  };
}
