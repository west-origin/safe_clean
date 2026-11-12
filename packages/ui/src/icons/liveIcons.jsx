// Shared live controls: Lucide 24-unit geometry, existing display sizes.
import { ChevronLeft as LiveBackIconGlyph } from 'lucide-react';
import { Settings as SettingsIconGlyph } from 'lucide-react';
import { UserRound as ViewerIconGlyph } from 'lucide-react';
import { House as HomeOutlineIconGlyph } from 'lucide-react';
import { Maximize as FullscreenIconGlyph } from 'lucide-react';
import { VolumeX as MuteSpeakerIconGlyph } from 'lucide-react';

export function LiveBackIcon({ size = 24, className }) {
  return <LiveBackIconGlyph size={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}

export function SettingsIcon({ size = 24, className }) {
  return <SettingsIconGlyph size={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}

export function ViewerIcon({ size = 13, className }) {
  return <ViewerIconGlyph size={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}

export function HomeOutlineIcon({ size = 18, className }) {
  return <HomeOutlineIconGlyph size={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}

export function FullscreenIcon({ size = 24, className }) {
  return <FullscreenIconGlyph size={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}

export function MuteSpeakerIcon({ size = 24, className }) {
  return <MuteSpeakerIconGlyph size={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
