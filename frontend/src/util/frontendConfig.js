function toNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export const MAX_FILES_PER_ACCOUNT = toNumber(import.meta.env.VITE_MAX_FILES_PER_ACCOUNT, 3);
export const STORAGE_CAPACITY_MB = toNumber(import.meta.env.VITE_STORAGE_CAPACITY_MB, 10);
export const MAX_ACTIVE_SHARE_LINKS = toNumber(import.meta.env.VITE_MAX_ACTIVE_SHARE_LINKS, 3);
export const MAX_FILE_SIZE_MB = toNumber(import.meta.env.VITE_MAX_FILE_SIZE_MB, 10);
export const UPLOAD_RATE_LIMIT_COUNT = toNumber(import.meta.env.VITE_UPLOAD_RATE_LIMIT_COUNT, 3);
export const UPLOAD_RATE_LIMIT_WINDOW_MINUTES = toNumber(import.meta.env.VITE_UPLOAD_RATE_LIMIT_WINDOW_MINUTES, 15);
