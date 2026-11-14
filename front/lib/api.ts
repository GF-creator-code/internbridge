/** Packaged pages use the API served alongside them. The standalone frontend uses the configured backend. */
export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ||
  (typeof window !== 'undefined' && window.location.port !== process.env.FRONTEND_PORT
    ? window.location.origin
    : process.env.API_BASE_URL || 'http://localhost:9527')

/** 拼接后端接口地址，path 以 / 开头，例如 apiUrl('/api/boss/config') */
export function apiUrl(path: string): string {
  return `${API_BASE}${path}`
}
