import {Component, EventEmitter, Input, OnInit, Output, output} from '@angular/core';
import {CurrencyPipe} from "@angular/common";
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';

@Component({
  selector: 'app-form-cliente',
  imports: [
    ReactiveFormsModule
  ],
  templateUrl: './form-cliente.html',
  styleUrl: './form-cliente.css',
})
export class FormCliente implements OnInit {

  constructor(formBuilder: FormBuilder) {
  }

  formGroup!: FormGroup;
  cargando: boolean = false;

  @Input() datosPrevios: any = null;

  @Output() siguiente = new EventEmitter<any>()
  @Output() atras = new EventEmitter<any>()

  ngOnInit(): void {
    this.formGroup = new FormBuilder().group({
      nombre: ['', [Validators.required, Validators.minLength(2)]],
      apellido: ['', [Validators.required, Validators.minLength(2)]],
      dni: ['', [Validators.required, Validators.pattern('^[0-9]{7,8}$')]],
      email: ['', [Validators.required, Validators.email]],
      telefono: ['', [Validators.required, Validators.pattern('^[0-9]{10,13}$')]],
      // Opcionales: no llevan Validators.required.
      edad: [null, [Validators.min(0), Validators.max(120)]],
      localidad: ['']
    });

    if (this.datosPrevios) {
      this.formGroup.patchValue(this.datosPrevios);
    }
  }
  get f() {
    return this.formGroup.controls;
  }

  onSiguiente(): void{
    if(this.formGroup.valid){
      this.siguiente.emit(this.formGroup.value);
    }
    else
    {
      this.formGroup.markAllAsTouched();
    }
  }

  onVolver(): void {
   this.atras.emit();
  }
}
