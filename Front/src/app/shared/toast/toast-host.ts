import { Component, inject } from '@angular/core';
import { LucideCircleAlert, LucideCircleCheck, LucideInfo, LucideX } from '@lucide/angular';
import { ToastService } from './toast.service';

/** Muestra los avisos de ToastService. Va una sola vez, en App. */
@Component({
  selector: 'app-toast-host',
  imports: [LucideCircleCheck, LucideCircleAlert, LucideInfo, LucideX],
  templateUrl: './toast-host.html',
  styleUrl: './toast-host.css',
})
export class ToastHost {
  readonly toast = inject(ToastService);
}
