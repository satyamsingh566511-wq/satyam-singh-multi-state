// eslint.config.js — ESLint 9 flat config.
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';

export default tseslint.config(
    { ignores: ['dist', 'node_modules', 'coverage', 'src/gql/generated'] },
    js.configs.recommended,
    ...tseslint.configs.recommendedTypeChecked,
    {
      files: ['**/*.{ts,tsx}'],
      languageOptions: {
        parserOptions: {
          project: ['./tsconfig.app.json', './tsconfig.node.json'],
          tsconfigRootDir: import.meta.dirname,
        },
      },
      settings: { react: { version: 'detect' } },
      plugins: {
        react,
        'react-hooks':   reactHooks,
        'react-refresh': reactRefresh,
      },
      rules: {
        'react/jsx-key':                          'error',
        'react-hooks/rules-of-hooks':             'error',
        'react-hooks/exhaustive-deps':            'warn',
        '@typescript-eslint/no-explicit-any':     'error',
        '@typescript-eslint/no-floating-promises':'error',
        'react-refresh/only-export-components':   ['warn', { allowConstantExport: true }],
      },
    },
    // Plain JS config files (this file, etc.) are not part of the TS project,
    // so turn off the type-checked rules that need parserServices for them.
    {
      files: ['**/*.js'],
      ...tseslint.configs.disableTypeChecked,
    },
);