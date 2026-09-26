import { Component, type ReactNode } from 'react'
export class MapaSeguro extends Component<{ children: ReactNode }, { error: boolean }> {
  override state = { error: false }
  static getDerivedStateFromError() { return { error: true } }
  override render() {
    return this.state.error ? <p className="mapa-aviso">No pudimos abrir el mapa interactivo. Los estados siguen en la lista.</p> : this.props.children
  }
}
