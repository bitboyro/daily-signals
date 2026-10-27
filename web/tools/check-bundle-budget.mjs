#!/usr/bin/env node
import { readdir, stat } from 'node:fs/promises';
import { resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('../dist/', import.meta.url)));
const totalBudget = 32 * 1024;
const fileBudget = 8 * 1024;

async function filesUnder(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = await Promise.all(entries.map((entry) => {
    const path = resolve(directory, entry.name);
    return entry.isDirectory() ? filesUnder(path) : Promise.resolve([path]);
  }));
  return files.flat().sort();
}

const files = await filesUnder(root);
const measured = await Promise.all(files.map(async (path) => ({
  path: relative(root, path),
  bytes: (await stat(path)).size,
})));
const totalBytes = measured.reduce((sum, file) => sum + file.bytes, 0);
const oversized = measured.filter((file) => file.bytes > fileBudget);

console.log(JSON.stringify({ totalBytes, totalBudget, fileBudget, files: measured }, null, 2));
if (totalBytes > totalBudget || oversized.length > 0) {
  const details = oversized.map((file) => `${file.path}=${file.bytes}`).join(', ');
  throw new Error(`web bundle budget exceeded: total=${totalBytes}/${totalBudget}${details ? `; ${details}` : ''}`);
}
