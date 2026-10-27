import { Component, ElementRef, inject, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageSwitcher } from '../language-switcher/language-switcher';
import { shopNavigation } from '../navigation';
import { AuthSessionService } from '../../core/auth/auth-session.service';

@Component({
  selector: 'app-mobile-menu',
  imports: [RouterLink, TranslocoPipe, LanguageSwitcher],
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

  open(): void {
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }
}
