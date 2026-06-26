// eslint.config.js — ESLint 9 flat config.
import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import jsxA11y from 'eslint-plugin-jsx-a11y';

// The `as any` ban: a TS type assertion (`x as any`) is a parser-level node, so
// it is matched syntactically rather than via a type rule. Shared across the
// app + tooling blocks so neither can smuggle one in.
const noAsAny = {
  selector: "TSTypeAssertion[typeAnnotation.typeName.name='any']",
  message: 'as any is banned; widen the type properly.',
};

export default tseslint.config(
  {
    ignores: [
      'dist',
      'node_modules',
      'coverage',
      '.vite',
      'playwright-report',
      'test-results',
      'e2e/.auth',
      'src/gql/generated',
    ],
  },
  js.configs.recommended,

  // ---- Application source: full type-checked rule set + React + a11y ----
  {
    files: ['src/**/*.{ts,tsx}'],
    extends: [...tseslint.configs.recommendedTypeChecked],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.app.json', './tsconfig.node.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      globals: { ...globals.browser },
    },
    settings: { react: { version: 'detect' } },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      'jsx-a11y': jsxA11y,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.configs.recommended.rules,
      'react/jsx-key': 'error',
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/no-floating-promises': 'error',
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
      'no-restricted-syntax': ['error', noAsAny],
    },
  },

  // ---- Test files: relax the dynamic-matcher unsafe rules ----
  // jest-axe / testing-library matchers resolve to loosely-typed surfaces that
  // trip no-unsafe-*; the no-explicit-any + as-any bans stay on.
  {
    files: ['src/**/*.test.{ts,tsx}', 'src/test/**/*.{ts,tsx}'],
    rules: {
      '@typescript-eslint/no-unsafe-call': 'off',
      '@typescript-eslint/no-unsafe-argument': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
      '@typescript-eslint/no-unsafe-return': 'off',
      '@typescript-eslint/require-await': 'off',
    },
  },

  // ---- Tooling / E2E (Node context, no app tsconfig project) ----
  {
    files: [
      'e2e/**/*.ts',
      'playwright.config.ts',
      '*.config.ts',
      'server/**/*.ts',
      'codegen.ts',
    ],
    extends: [...tseslint.configs.recommended],
    languageOptions: { globals: { ...globals.node } },
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      'no-restricted-syntax': ['error', noAsAny],
    },
  },

  // Plain JS config files are not part of any TS project, so drop the
  // type-checked rules that need parserServices for them.
  {
    files: ['**/*.js'],
    ...tseslint.configs.disableTypeChecked,
  },
);
