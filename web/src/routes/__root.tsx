import { type ReactNode } from 'react'
import { Outlet, useLocation } from '@tanstack/react-router'
import { AnimatePresence } from 'framer-motion'
import { Shell } from '@/components/layout/Shell'
import { Topbar } from '@/components/layout/topbar'
import { NodeSocketContext, useNodeSocketProvider } from '@/hooks/useNodeSocket'
import { ThemeProvider } from '@/theme/ThemeProvider'
import { AudioProvider } from '@/audio'

function NodeSocketProvider({ children }: { children: ReactNode }) {
  const value = useNodeSocketProvider()
  return <NodeSocketContext.Provider value={value}>{children}</NodeSocketContext.Provider>
}

export default function RootLayout() {
  const location = useLocation()

  return (
    <ThemeProvider>
      <AudioProvider>
        <NodeSocketProvider>
          <Shell>
            <Topbar />
            <AnimatePresence mode="wait">
              <Outlet key={location.pathname} />
            </AnimatePresence>
          </Shell>
        </NodeSocketProvider>
      </AudioProvider>
    </ThemeProvider>
  )
}
