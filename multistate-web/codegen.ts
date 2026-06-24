import type { CodegenConfig } from '@graphql-codegen/cli';

const config: CodegenConfig = {
  // The live endpoint (http://localhost:8080/graphql) isn't serving
  // introspection locally, so we generate against a local SDL copy —
  // "Codegen without a running backend" (Appendix).
  schema: './schema.graphql',
  documents: ['./src/queries/**/*.graphql'],
  ignoreNoDocuments: true,
  generates: {
    './src/gql/generated/': {
      preset: 'client',
      presetConfig: {
        gqlTagName: 'graphql',
      },
      // The web tsconfig enables verbatimModuleSyntax, so generated
      // type imports must be `import type`.
      config: {
        useTypeImports: true,
      },
    },
  },
};

export default config;
