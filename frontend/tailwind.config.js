/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        blue: {
          50: '#f2f7f8',
          100: '#dce9eb',
          200: '#b9d2d7',
          300: '#8bafb8',
          400: '#5d8995',
          500: '#3b6c79',
          600: '#285562',
          700: '#1f4350',
          800: '#193641',
          900: '#142c36',
          950: '#091a22',
        },
      },
      fontFamily: {
        // 中文投研界面优先使用系统黑体，兼顾 Windows 与 macOS 的清晰度
        sans: [
          '"Aptos"',
          'system-ui', 
          '-apple-system', 
          'BlinkMacSystemFont', 
          '"Segoe UI"', 
          'Roboto', 
          '"Helvetica Neue"', 
          'Arial', 
          '"Noto Sans"', 
          '"PingFang SC"',    // Mac 中文
          '"Hiragino Sans GB"', // Mac 中文
          '"Microsoft YaHei"',  // Win 中文
          '"WenQuanYi Micro Hei"', 
          'sans-serif'
        ],
        // 标题与数据使用更窄、更像研究终端的字体栈
        display: ['"Arial Narrow"', '"DIN Alternate"', '"Microsoft YaHei"', 'system-ui', 'sans-serif'],
        mono: ['"Cascadia Mono"', '"JetBrains Mono"', 'Menlo', 'Consolas', 'monospace'],
      },
      typography: {
        DEFAULT: {
          css: {
            maxWidth: 'none',
            color: '#33444a',
            // 基础行高设置
            p: {
              marginTop: '1.2em',
              marginBottom: '1.2em',
              lineHeight: '1.75', 
              letterSpacing: '0.01em', // 稍微加一点字间距提升呼吸感
            },
            // 标题设置
            h1: {
              color: '#12272f',
              fontWeight: '800',
              marginTop: '0',
              marginBottom: '0.8em',
              lineHeight: '1.2',
            },
            h2: {
              color: '#193641',
              fontWeight: '700',
              marginTop: '2em',
              marginBottom: '1em',
              lineHeight: '1.3',
            },
            h3: {
              color: '#193641',
              fontWeight: '600',
              marginTop: '1.5em',
              marginBottom: '0.6em',
            },
            // 列表
            li: {
              marginTop: '0.5em',
              marginBottom: '0.5em',
            },
          },
        },
      },
    },
  },
  plugins: [
    require('@tailwindcss/typography'),
  ],
}
