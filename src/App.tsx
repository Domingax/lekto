import { RouterProvider, createBrowserRouter } from 'react-router-dom'

function App({ router }: { router: ReturnType<typeof createBrowserRouter> }) {
  return <RouterProvider router={router} />
}

export default App
