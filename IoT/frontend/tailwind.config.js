/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{vue,js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            screens: {
                'iot': '2140px',
            },
            width: {
                'device': '2140px',
            },
            height: {
                'device': '1440px',
            },
            colors: {
                primary: "var(--color-primary)",
                "primary-light": "var(--color-primary-light)",
                "primary-soft": "var(--color-primary-soft)",
                "text-title": "var(--text-title)",
                "text-body": "var(--text-body)",
                "text-sub": "var(--text-sub)",
                "border-default": "var(--border-default)",
                "bg-page": "var(--bg-page)",
                primaryBg: "#FFF6EF",
                "background-light": "#FFFBF7",
                "background-dark": "#1A1614",
                emergency: "#f20d0d",
                // 달력 설정
                "surface-light": "#FFFFFF",
                "surface-dark": "#26221F",
                "accent-peach": "#FFD8B1",
                "accent-sage": "#D4E6D9",
                "accent-lavender": "#E6E1F9",
                // 달력설정
            },
            fontFamily: {
                sans: ['Pretendard', '-apple-system', 'BlinkMacSystemFont', 'system-ui', 'Roboto', 'Helvetica Neue', 'Segoe UI', 'Apple SD Gothic Neo', 'Noto Sans KR', 'Malgun Gothic', 'sans-serif'],
                display: ['Lexend', 'sans-serif'],
            },
        },
    },
    plugins: [],
}
