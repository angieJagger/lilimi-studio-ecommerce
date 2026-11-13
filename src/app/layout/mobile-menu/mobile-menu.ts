import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { shopNavigation } from '../navigation';
import { AuthSessionService } from '../../core/auth/auth-session.service';

@Component({
  selector: 'app-mobile-menu',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './mobile-menu.html',
  styleUrl: './mobile-menu.scss',
})
export class MobileMenu {
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly transloco = inject(TranslocoService);
  protected readonly session = inject(AuthSessionService);
  protected readonly shopNavigation = shopNavigation.filter(
    (item) => item.path !== 'account/login',
  );

  readonly isOpen = signal(false);

  open(trigger: HTMLButtonElement): void {
    const dialog = this.dialog().nativeElement;
    const { top, left } = trigger.getBoundingClientRect();
    dialog.style.setProperty('--menu-toggle-top', `${top}px`);
    dialog.style.setProperty('--menu-toggle-left', `${left}px`);
    dialog.showModal();
    this.isOpen.set(true);
  }

  close(): void {
    this.dialog().nativeElement.close();
  }
}
