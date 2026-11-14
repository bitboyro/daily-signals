import { defineConfig } from '@playwright/test';

const localChromiumChannel = process.env.PLAYWRIGHT_CHROMIUM_CHANNEL;

export default defineConfig({
  testDir: './visual',
  outputDir: 'reports/visual-results',
  reporter: [['line'], ['junit', { outputFile: 'reports/web-visual.xml' }]],
  snapshotPathTemplate: '{testDir}/__screenshots__/{arg}{ext}',
  expect: { toHaveScreenshot: { animations: 'disabled', maxDiffPixelRatio: 0.04, threshold: 0.2 } },
  use: {
    browserName: 'chromium',
    viewport: { width: 1280, height: 900 },
    colorScheme: 'light',
    deviceScaleFactor: 1,
    locale: 'en-GB',
    timezoneId: 'UTC',
    reducedMotion: 'reduce',
    ...(localChromiumChannel ? { channel: localChromiumChannel } : {}),
  },
});
