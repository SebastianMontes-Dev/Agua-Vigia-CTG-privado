import '@testing-library/jest-dom/vitest'

afterEach(() => {
  window.localStorage.clear()
  window.sessionStorage.clear()
  delete document.documentElement.dataset.theme
})
