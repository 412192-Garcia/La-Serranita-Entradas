import {Component, EventEmitter, Input, OnInit, Output, output} from '@angular/core';
import {CurrencyPipe} from "@angular/common";
import {AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators} from '@angular/forms';
import {Spinner} from '../../shared/spinner/spinner';

/**
 * Acepta cualquier formato humano de teléfono (con espacios, guiones, paréntesis,
 * "+" inicial de código de país) — sólo cuenta los dígitos reales y exige que haya
 * una cantidad razonable (8 a 15, el rango de números de teléfono en general).
 */
function telefonoValidator(control: AbstractControl): ValidationErrors | null {
  const valor = (control.value ?? '').trim();
  if (!valor) return null; // los campos opcionales no tienen por qué tener valor; Validators.required ya cubre los obligatorios
  const cantidadDigitos = valor.replace(/\D/g, '').length;
  return cantidadDigitos >= 8 && cantidadDigitos <= 15 ? null : { telefonoInvalido: true };
}

/** Deja sólo lo que identifica al documento (letras y números), sacando puntos, espacios
 * y guiones usados como separador visual — sirve tanto para DNI argentino como para
 * pasaporte/cédula extranjera. Mayúsculas para uniformar (los pasaportes suelen tener
 * letras, ej. "AAB123456"). */
function normalizarDocumento(valor: string | null | undefined): string {
  return (valor ?? '').toString().replace(/[.\s-]/g, '').toUpperCase();
}

/** Acepta DNI argentino (7 u 8 dígitos, con puntos o espacios como separador, ej. "46.308.241")
 * o un documento extranjero (pasaporte/cédula: letras y números, sin un formato fijo entre países). */
function dniValidator(control: AbstractControl): ValidationErrors | null {
  const valor = (control.value ?? '').toString().trim();
  if (!valor) return null; // Validators.required ya cubre el caso vacío en los campos obligatorios
  const limpio = normalizarDocumento(valor);
  return /^[A-Z0-9]{6,15}$/.test(limpio) ? null : { dniInvalido: true };
}

/** El campo de "confirmar DNI" tiene que coincidir con el documento de su mismo grupo (ignorando formato). */
function confirmacionDniValidator(control: AbstractControl): ValidationErrors | null {
  const valor = normalizarDocumento(control.value);
  if (!valor) return null; // Validators.required ya cubre el caso vacío
  const original = normalizarDocumento(control.parent?.get('dni')?.value);
  return valor === original ? null : { dniNoCoincide: true };
}

@Component({
  selector: 'app-form-cliente',
  imports: [
    ReactiveFormsModule,
    Spinner
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
      dni: ['', [Validators.required, dniValidator]],
      // El DNI es lo que valida el ingreso al parque: pedirlo dos veces (como una
      // contraseña) atrapa errores de tipeo antes de que lleguen a generar la reserva.
      dniConfirmacion: ['', [Validators.required, confirmacionDniValidator]],
      email: ['', [Validators.required, Validators.email]],
      telefono: ['', [Validators.required, telefonoValidator]],
      // Opcionales: no llevan Validators.required.
      edad: [null, [Validators.min(0), Validators.max(120)]],
      localidad: [''],
      receptor: this.fb.group({
        nombre: ['', this.esRegalo ? [Validators.required, Validators.minLength(2)] : []],
        dni: ['', this.esRegalo ? [Validators.required, dniValidator] : []],
        dniConfirmacion: ['', this.esRegalo ? [Validators.required, confirmacionDniValidator] : []],
        email: ['', this.esRegalo ? [Validators.required, Validators.email] : []],
        // Opcional siempre.
        telefono: ['', telefonoValidator],
      }),
    });

    if (this.datosPrevios) {
      this.formGroup.patchValue(this.datosPrevios);
    }
    if (this.datosPreviosReceptor) {
      this.formGroup.get('receptor')!.patchValue(this.datosPreviosReceptor);
    }

    // Si corrige el DNI original después de haber tipeado la confirmación, hay que
    // volver a chequear que sigan coincidiendo (los validators no se re-evalúan solos
    // cuando cambia OTRO control, sólo el propio).
    this.formGroup.get('dni')!.valueChanges.subscribe(() =>
      this.formGroup.get('dniConfirmacion')!.updateValueAndValidity());
    this.formGroup.get('receptor.dni')!.valueChanges.subscribe(() =>
      this.formGroup.get('receptor.dniConfirmacion')!.updateValueAndValidity());
  }
  get f() {
    return this.formGroup.controls;
  }

  get fReceptor() {
    return (this.formGroup.get('receptor') as FormGroup).controls;
  }

  onSiguiente(): void{
    if(this.formGroup.valid){
      const { receptor, dniConfirmacion, ...cliente } = this.formGroup.value;
      // Se limpia el separador visual (puntos, espacios, guiones) antes de mandarlo.
      // dniConfirmacion no se manda: sólo existía para que el usuario se auto-corrija antes de enviar.
      cliente.dni = normalizarDocumento(cliente.dni);

      let receptorLimpio = null;
      if (this.esRegalo && receptor) {
        const { dniConfirmacion: _receptorConfirmacion, ...resto } = receptor;
        resto.dni = normalizarDocumento(resto.dni);
        receptorLimpio = resto;
      }

      this.siguiente.emit({ cliente, receptor: receptorLimpio });
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
