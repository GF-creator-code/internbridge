import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  title: 'InternBridge - 本地投递工作台',
  description: '本地运行的跨平台职位投递管理端：统一配置、七个平台、投递进度与每日日报。',
  
  // GitHub Pages 使用仓库路径，本地开发使用根路径。
  base: process.env.DOCS_BASE || (process.env.GITHUB_ACTIONS ? '/' + (process.env.GITHUB_REPOSITORY?.split('/')[1] || 'internbridge') + '/' : '/'),
  lastUpdated: true,

  vite: {
    server: {
      watch: {
        ignored: ['**/dist/**', '**/.vitepress/dist/**']
      }
    }
  },

  head: [
    ['link', { rel: 'icon', href: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><text y=".9em" font-size="90">🍀</text></svg>' }],
    ['meta', { name: 'keywords', content: 'InternBridge, 职位投递, 打招呼语, Boss直聘, 猎聘, 智联招聘, 前程无忧, JobsDB, OfferToday, 开源求职助手' }],
    ['meta', { name: 'author', content: 'InternBridge' }],
    ['meta', { name: 'robots', content: 'index,follow' }]
  ],

  themeConfig: {
    logo: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><text y=".9em" font-size="90">🍀</text></svg>',
    siteTitle: 'InternBridge',

    // 内置本地离线全文搜索（支持中文分词与提示词）
    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: {
                buttonText: '搜索文档',
                buttonAriaLabel: '搜索文档'
              },
              modal: {
                noResultsText: '无法找到相关结果',
                resetButtonTitle: '清除查询条件',
                footer: {
                  selectText: '选择',
                  navigateText: '切换',
                  closeText: '关闭'
                }
              }
            }
          }
        }
      }
    },

    // 顶部导航栏
    nav: [
      { text: '首页', link: '/' },
      { text: '项目介绍', link: '/guide/introduction' },
      { text: '🚀 快速开始', link: '/guide/quick-start' },
      { text: '⚙️ 配置说明', link: '/guide/config' },
      { text: '❓ 常见问题', link: '/guide/faq' },
      {
        text: '相关资源',
        items: [
          { text: 'GitHub 仓库', link: 'https://github.com/GF-creator-code/internbridge' },
          { text: '界面截图', link: '/guide/screenshots' },
          { text: '社区与贡献', link: '/guide/community' },
          { text: '更新日志', link: '/guide/changelog' }
        ]
      }
    ],

    // 侧边栏结构化配置
    sidebar: [
      {
        text: '📖 指南与入门',
        items: [
          { text: '项目介绍与背景', link: '/guide/introduction' },
          { text: '快速上手', link: '/guide/quick-start' }
        ]
      },
      {
        text: '⚙️ 设置与配置',
        items: [
          { text: '核心与各平台配置', link: '/guide/config' },
          { text: 'AI 大模型与消息推送', link: '/guide/ai-and-push' }
        ]
      },
      {
        text: '🛡️ 进阶与运维',
        items: [
          { text: '常见问题与排错指南', link: '/guide/faq' },
          { text: '维护与发布', link: '/guide/maintenance' },
          { text: '更新日志与版本历史', link: '/guide/changelog' },
          { text: '界面与运行截图', link: '/guide/screenshots' },
          { text: '社区与贡献', link: '/guide/community' },
        ]
      }
    ],

    // 编辑与更新配置
    editLink: {
      pattern: 'https://github.com/GF-creator-code/internbridge/edit/main/docs/:path',
      text: '在 GitHub 上编辑此页'
    },
    lastUpdated: {
      text: '最后更新于',
      formatOptions: {
        dateStyle: 'short',
        timeStyle: 'medium'
      }
    },

    // 社交链接
    socialLinks: [
      { icon: 'github', link: 'https://github.com/GF-creator-code/internbridge' }
    ],

    // 页脚配置
    footer: {
      message: 'Released under the PolyForm Noncommercial 1.0.0 License.',
      copyright: 'Copyright © 2026 InternBridge'
    },

    docFooter: {
      prev: '上一篇',
      next: '下一篇'
    },
    outline: {
      label: '页面导航',
      level: [2, 3]
    }
  }
})
