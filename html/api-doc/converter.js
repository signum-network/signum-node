class Converter {

  _value = 0.0
  _decimals = 8
  _quantity = 0


  get value() {
    return this._value
  }

  get quantity() {
    return this._quantity
  }

  get decimals() {
    return this._decimals
  }

  dispatch() {
    document.dispatchEvent(new CustomEvent('convert', {
      detail: {
        quantity: this.quantity,
        value: this.value,
        decimals: this.decimals,
      }
    }))
  }

  setDecimals(d){
    this._decimals = d
    this._quantity = this._value * ( 10 ** this._decimals)
    this._value = this._quantity / (10 ** this._decimals)
    this.dispatch()
  }

  setValue(v){
    this._value = v
    this._quantity = this._value * ( 10 ** this._decimals)
    this.dispatch()
  }

  setQuantity(q){
    this._quantity = q
    this._value = this._quantity / (10 ** this._decimals)
    this.dispatch()
  }
}

window.converter = new Converter()
