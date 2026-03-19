import vgLogo from '../assets/images/SVG/VG.svg'

const DISCORD_CONTACT = 'https://discord.gg/ag3z5F35tb'
const YOUTUBE_URL = 'https://www.youtube.com/channel/UCzhTNgfg5JSDGpHsjovgwQQ/videos'

const currentYear = new Date().getFullYear()

export function Footer() {
  return (
    <footer className="footer">
      <div className="container">
        <img className="footer__logo" src={vgLogo} alt="VG" />
        <div className="footer__info">
          <p>
            Site created by fans to make Star Wars Battlefront 2015 more competitive with a ranked
            game system and rating players for balanced&nbsp;games
          </p>
          <div className="contact">
            <h3 className="title title--small">Any questions or suggestions ?</h3>
            <div>
              <a className="btn btn--contact link" href={DISCORD_CONTACT} target="_blank" rel="noopener noreferrer">
                Contact&nbsp;Us
              </a>
            </div>
          </div>
        </div>
        <div className="footer__menu">
          <small className="footer__title">Menu</small>
          <ul className="list menu__list">
            <li className="menu__element">
              <a className="link" href="/">Play</a>
            </li>
            <li className="menu__element">
              <a className="link" href="/matches">Matches</a>
            </li>
            <li className="menu__element">
              <a className="link" href="/stats">Stats</a>
            </li>
            <li className="menu__element">
              <a className="link" href="/about">About</a>
            </li>
          </ul>
        </div>
        <div className="footer__creator">
          <small className="footer__title">Creators</small>
          <ul className="list creator__list">
            <li className="creator__element">Tango</li>
            <li className="creator__element">Basse</li>
          </ul>
        </div>
        <div className="footer__bottom">
          <small className="copyright">
            Very Glitchy <span className="copyright--year">©{currentYear}</span>
          </small>
          <ul className="list media__list">
            <li className="media__element">
              <a className="link" href={DISCORD_CONTACT} target="_blank" rel="noopener noreferrer">
                Discord
              </a>
            </li>
            <li className="media__element">
              <a className="link" href={YOUTUBE_URL} target="_blank" rel="noopener noreferrer">
                Youtube
              </a>
            </li>
          </ul>
        </div>
      </div>
    </footer>
  )
}
