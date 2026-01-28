/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: "var(--color-primary)",
        "primary-light": "var(--color-primary-light)",
        "primary-soft": "var(--color-primary-soft)",
        "text-title": "var(--text-title)",
        "text-body": "var(--text-body)",
        "text-sub": "var(--text-sub)",
        "border-default": "var(--border-default)",
        "bg-page": "var(--bg-page)",
      },
      fontFamily: {
        sans: ['Pretendard', '-apple-system', 'BlinkMacSystemFont', 'system-ui', 'Roboto', 'Helvetica Neue', 'Segoe UI', 'Apple SD Gothic Neo', 'Noto Sans KR', 'Malgun Gothic', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
