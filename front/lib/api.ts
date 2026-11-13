// A packaged dashboard and its API share an origin. The dev server uses port 6866.
export const API_BASE = process.env.NEXT_PUBLIC_API_BASE ??
  (typeof window !== 'undefined' && window.location.port === '6866'
    ? process.env.API_BASE_URL || 'http://127.0.0.1:9527'
    : '')

export function apiUrl(path: string): string {
  return API_BASE + path
}
