import React from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from '@tanstack/react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { router } from './router'
import './i18n'
import './index.css'
import { NodeApiError } from '@/lib/nodeApi'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // an error answer from the node (NodeApiError) will not change on a retry
      retry: (failureCount, error) => !(error instanceof NodeApiError) && failureCount < 2,
      staleTime: 5_000,
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </React.StrictMode>,
)
