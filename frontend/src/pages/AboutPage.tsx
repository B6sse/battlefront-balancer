import { useState } from 'react'

type TabId = 'credits1' | 'credits2' | 'credits3' | 'credits4'

const TABS: { id: TabId; label: string; title: string; description: string }[] = [
  {
    id: 'credits1',
    label: 'Tango',
    title: 'Tango',
    description:
      'Tango is a professional front-end developer. He designed the website by adapting the interface of Star Wars Battlefront, and contributed to the functionalities and setting up of the database system.',
  },
  {
    id: 'credits2',
    label: 'Basse',
    title: 'Basse',
    description:
      'Basse handled the functionalities of the website. Most of his work lies within the battlefront balancer, where he coded in javascript to make the algorithms for the team making and more. He also played an important role in setting up the database system, allowing players to be added, updated, and deleted by admins with user accounts on the website.',
  },
  {
    id: 'credits3',
    label: 'Breleger',
    title: 'Breleger',
    description:
      'Breleger assisted in the development of the website. He is responsible for registering every player in the database. He also handles updates of the players, and he made graphic designs such as the custom Star Wars Battlefront cursor.',
  },
  {
    id: 'credits4',
    label: 'Purpoz',
    title: 'Purpoz',
    description:
      'We want to give a special thanks to Purpoz for sponsoring our website. He helped cover the expenses of hosting our project, and we are very grateful for his contribution.',
  },
]

export function AboutPage() {
  const [activeTab, setActiveTab] = useState<TabId>('credits1')

  return (
    <main>
      <section className="section section--credits">
        <div className="container">
          <h2 className="title title--large">About</h2>
          <div className="author">
            <ul className="list author__list">
              {TABS.map((tab) => (
                <li key={tab.id}>
                  <button
                    type="button"
                    className={`btn btn--width btn--tab sound__hover sound__click${activeTab === tab.id ? ' btn--active' : ''}`}
                    onClick={() => setActiveTab(tab.id)}
                  >
                    {tab.label}
                  </button>
                </li>
              ))}
            </ul>
          </div>
          <div className="author__content">
            {TABS.map((tab) => (
              <div
                key={tab.id}
                className={`author__tabs${activeTab === tab.id ? ' author__tabs--active' : ''}`}
              >
                <div>
                  <h3 className="title title--small">{tab.title}</h3>
                  <span>{tab.description}</span>
                </div>
              </div>
            ))}
          </div>
          <div className="feedback">
            If you have questions, comments, or suggestions regarding our website, don't hesitate to
            contact us on the VG Discord server.
          </div>
        </div>
      </section>
    </main>
  )
}
