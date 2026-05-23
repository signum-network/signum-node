import { createRouter, createRootRoute, createRoute, redirect } from '@tanstack/react-router'
import RootLayout from './routes/__root'
import DashboardPage from './routes/index'
import NetworkPage from './routes/network/index'

const rootRoute = createRootRoute({ component: RootLayout })

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: DashboardPage,
})

const networkRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/network',
  component: NetworkPage,
})

// Future routes — redirect to home until implemented
const blocksRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/blocks',
  beforeLoad: () => { throw redirect({ to: '/' }) },
})

const walletRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/wallet',
  beforeLoad: () => { throw redirect({ to: '/' }) },
})

const catchAllRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '*',
  beforeLoad: () => { throw redirect({ to: '/' }) },
})

export const routeTree = rootRoute.addChildren([
  indexRoute,
  networkRoute,
  blocksRoute,
  walletRoute,
  catchAllRoute,
])

export const router = createRouter({
  routeTree,
  basepath: import.meta.env.BASE_URL.replace(/\/$/, '') || '/',
  defaultPreload: 'intent',
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
