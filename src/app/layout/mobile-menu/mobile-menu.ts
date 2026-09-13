import { Component, ElementRef, inject, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { shopNavigation } from '../navigation';

@Component({
  selector: 'app-mobile-menu',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './mobile-menu.html',
  styleUrl: './mobile-menu.scss',
})
export class MobileMenu {
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly transloco = inject(TranslocoService);
  protected readonly shopNavigation = shopNavigation;

  open(): void {
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }
}
