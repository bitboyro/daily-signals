import { defineConfig } from '@playwright/test';

const localChromiumChannel = process.env.PLAYWRIGHT_CHROMIUM_CHANNEL;

export default defineConfig({
  testDir: './a11y',
  outputDir: 'reports/a11y-results',
  reporter: [['line'], ['junit', { outputFile: 'reports/web-a11y.xml' }]],
  use: {
    baseURL: 'http://127.0.0.1:8151',
    browserName: 'chromium',
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    ...(localChromiumChannel ? { channel: localChromiumChannel } : {}),
  },
  webServer: {
    command: 'java -cp ../radio-api/target/classes:../radio-edition/target/classes:../radio-editorial/target/classes:../radio-ingestion/target/classes:../radio-persistence/target/classes:../radio-api/target/runtime-libs/* ro.bitboy.dailysignals.DailySignals 8151',
    url: 'http://127.0.0.1:8151/health',
    reuseExistingServer: false,
    timeout: 30_000,
    env: {
      DAILY_SIGNALS_WEB_ROOT: `${process.cwd()}/dist`,
      DAILY_SIGNALS_REVIEW_TOKENS: 'nightly-producer-token-0001=PRODUCER',
    },
  },
});
