"use client";

import "./globals.css";
import Sidebar from "./components/Sidebar";
import ContentArea from "./components/ContentArea";
import { ThemeProvider } from "next-themes";
import { MotionConfig } from "framer-motion";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <head>
        <title>Get Jobs - 配置管理中心</title>
        <meta name="description" content="香港与内地实习申请管理，整理简历技能、匹配职位并查看投递记录" />
        <link
          rel="icon"
          href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>🍀</text></svg>"
          type="image/svg+xml"
        />
      </head>
      <body suppressHydrationWarning className="dark:bg-blacksection">
        <ThemeProvider
          attribute="class"
          defaultTheme="light"
          enableSystem={false}
        >
          <MotionConfig reducedMotion="user">
          <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-background focus:p-3">跳到主要内容</a>
          <div className="flex min-h-screen">
            <Sidebar />
            <ContentArea>
              {children}
            </ContentArea>
          </div>
          </MotionConfig>
        </ThemeProvider>
      </body>
    </html>
  );
}
