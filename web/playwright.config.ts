import { defineConfig } from '@playwright/test';

const viewports = {
  mobile: { width: 390, height: 844 },
  desktop: { width: 1440, height: 900 },
};
const localChromiumChannel = process.env.PLAYWRIGHT_CHROMIUM_CHANNEL;

export default defineConfig({
  testDir: './browser',
  outputDir: 'reports/playwright-results',
  reporter: [['line'], ['junit', { outputFile: 'reports/playwright.xml' }]],
  use: { baseURL: 'http://127.0.0.1:8151', trace: 'retain-on-failure' },
  webServer: {
    command: 'java -cp ../radio-api/target/classes:../radio-edition/target/classes:../radio-editorial/target/classes:../radio-ingestion/target/classes ro.bitboy.dailysignals.DailySignals 8151',
    url: 'http://127.0.0.1:8151/health',
    reuseExistingServer: false,
    timeout: 30_000,
    env: {
      DAILY_SIGNALS_WEB_ROOT: `${process.cwd()}/dist`,
      DAILY_SIGNALS_REVIEW_TOKENS: 'nightly-producer-token-0001=PRODUCER',
    },
  },
  projects: ['chromium', 'firefox', 'webkit'].flatMap((browserName) =>
    Object.entries(viewports).map(([profile, viewport]) => ({
      name: `${browserName}-${profile}`,
      use: { browserName: browserName as 'chromium' | 'firefox' | 'webkit', viewport,
        ...(browserName === 'chromium' && localChromiumChannel ? { channel: localChromiumChannel } : {}) },
    }))),
});
