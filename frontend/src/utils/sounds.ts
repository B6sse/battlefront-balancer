import hoverMp3 from '../assets/sounds/hover.mp3'
import clickMp3 from '../assets/sounds/add.mp3'
import deleteMp3 from '../assets/sounds/delete.mp3'

function play(src: string, volume: number) {
  const audio = new Audio(src)
  audio.volume = volume
  audio.play().catch(() => {
    // Browsers block autoplay until the user has interacted with the page — ignore silently.
  })
}

export const playHover = () => play(hoverMp3, 0.1)
export const playClick = () => play(clickMp3, 0.25)
export const playDelete = () => play(deleteMp3, 0.25)
