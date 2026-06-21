# Computer Vision Final Project
## Caleb Griffin

### Overview
The TCGPlayer's scanner does not work very well on my phone, which is a Samsung Galaxy S23. In the course of this project, I will produce a scanning application that will actually identify cards, more than one at a time and create a digital database of the cards in my posession. I will then be able to export my lists to include in other card management solutions, like Moxfield.com.  
  
I intend to perform a normal pipeline of segmentation, perspective correction, and then apply further segmentation to identify different sections of a Magic: The Gathering trading card. This data will then be compared to a publicly available database of Magic: The Gathering cards.  
  
Initial literature review shows a common approach of using Convolutional Neural Networks (CNNs) for optical card recognition. TCG Player uses a proprietary technology called Roca Vision to identify cards from several Trading Card Games, with Magic: The Gathering being one of them.  
  
Alternative approachs to card detection are Optical Character Recognition, which would identify cards based on the text present on the card or Preceptual Hashing, which takes an average hash of a card image and compares it to a known database of card hashes.

The alternative approaches save computational time and effort, compared to a CNN approach, but are more limited in feature sets and detection capability. I will be using the CNN approach because I care more about accuracy and being able to scan multiple images simultaneously. I may also try to implement scans on images that are captured via video, allowing me to flip through a binder and capture every card in the binder without having to collect individual images.
