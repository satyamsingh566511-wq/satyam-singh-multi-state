// vitest.config.ts
import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config';

export default mergeConfig(
    viteConfig,
    defineConfig({
      test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: ['./src/test/setupTests.ts'],
        include: ['src/**/*.test.{ts,tsx}'],
        coverage: {
          provider: 'v8',
          reporter: ['text', 'html', 'lcov'],
          include: ['src/**/*.{ts,tsx}'],
          exclude: [
            'src/**/*.test.{ts,tsx}',
            'src/test/**',
            'src/gql/generated/**',
            // The MSW worker + composition root / ambient declarations carry no
            // testable branches — excluding them keeps the coverage signal on
            // real logic rather than bootstrap glue.
            '**/mockServiceWorker.js',
            'src/main.tsx',
            'src/queryClient.ts',
            'src/vite-env.d.ts',
            'src/types/**',
          ],
          thresholds: {
            // The W4 capstone gate. Branch coverage is the load-bearing
            // metric; lines/funcs come along for the ride.
            branches:   70,
            lines:      75,
            functions:  75,
            statements: 75,
          },
        },
      },
    }),
);