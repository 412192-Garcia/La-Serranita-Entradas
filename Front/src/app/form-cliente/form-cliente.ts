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

  constructor(private fb: FormBuilder) {
  }

  formGroup!: FormGroup;
  cargando: boolean = false;

  @Input() datosPrevios: any = null;
  /** Cuando es un regalo, se piden también los datos de quien lo recibe (para avisarle por mail). */
  @Input() esRegalo: boolean = false;
  @Input() datosPreviosReceptor: any = null;

  @Output() siguiente = new EventEmitter<any>()
  @Output() atras = new EventEmitter<any>()

  ngOnInit(): void {
    this.formGroup = this.fb.group({
      nombre: ['', [Validators.required, Validators.minLength(2)]],
      apellido: ['', [Validators.required, Validators.minLength(2)]],
      dni: ['', [Validators.required, Validators.pattern('^[0-9]{7,8}$')]],
      email: ['', [Validators.required, Validators.email]],
      telefono: ['', [Validators.required, Validators.pattern('^[0-9]{10,13}$')]],
      // Opcionales: no llevan Validators.required.
      edad: [null, [Validators.min(0), Validators.max(120)]],
      localidad: [''],
      receptor: this.fb.group({
        nombre: ['', this.esRegalo ? [Validators.required, Validators.minLength(2)] : []],
        dni: ['', this.esRegalo ? [Validators.required, Validators.pattern('^[0-9]{7,8}$')] : []],
        email: ['', this.esRegalo ? [Validators.required, Validators.email] : []],
        // Opcional siempre.
        telefono: [''],
      }),
    });

    if (this.datosPrevios) {
      this.formGroup.patchValue(this.datosPrevios);
    }
    if (this.datosPreviosReceptor) {
      this.formGroup.get('receptor')!.patchValue(this.datosPreviosReceptor);
    }
  }
  get f() {
    return this.formGroup.controls;
  }

  get fReceptor() {
    return (this.formGroup.get('receptor') as FormGroup).controls;
  }

  onSiguiente(): void{
    if(this.formGroup.valid){
      const { receptor, ...cliente } = this.formGroup.value;
      this.siguiente.emit({ cliente, receptor: this.esRegalo ? receptor : null });
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
